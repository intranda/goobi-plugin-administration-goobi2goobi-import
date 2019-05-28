package de.intranda.goobi.plugins;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.walkFileTree;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.goobi.beans.Docket;
import org.goobi.beans.Ldap;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Ruleset;
import org.goobi.beans.User;
import org.goobi.beans.Usergroup;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.primefaces.event.FileUploadEvent;

import de.intranda.goobi.importrules.DocketConfigurationItem;
import de.intranda.goobi.importrules.InfrastructureImportConfiguration;
import de.intranda.goobi.importrules.LdapConfigurationItem;
import de.intranda.goobi.importrules.ProjectConfigurationItem;
import de.intranda.goobi.importrules.Rule;
import de.intranda.goobi.importrules.RulesetConfigurationItem;
import de.intranda.goobi.importrules.UsergroupConfigurationItem;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.persistence.managers.DocketManager;
import de.sub.goobi.persistence.managers.LdapManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import de.sub.goobi.persistence.managers.RulesetManager;
import de.sub.goobi.persistence.managers.UserManager;
import de.sub.goobi.persistence.managers.UsergroupManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class ImportInfrastructureDataPlugin implements IAdministrationPlugin {
    // upload zip file
    // extract zip file
    // read xml file
    // check content
    // check if content already exist
    // overwrite?
    // check for files
    // check if files already exist
    // overwrite?
    @Getter
    private String title = "goobi-plugin-administration-upload-infrastructure";

    @Getter
    private PluginType type = PluginType.Administration;

    @Getter
    private String gui = "/uii/administration-upload-infrastructure.xhtml";

    @Getter
    private String filename;

    private Path importFile;

    @Getter
    private boolean uploadHasErrors = false;

    private Path xmlFile;
    private Path dockets;
    private Path rulesets;

    private List<Ldap> allExistingLdaps;
    private List<Ldap> newLdaps;

    @Getter
    private int numberOfNewLdaps;
    @Getter
    private int totalNumberOfLdaps;

    private List<Project> allExistingProjects;
    private List<Project> newProjects;
    @Getter
    private int numberOfNewProjects;
    @Getter
    private int totalNumberOfProjects;

    private List<Ruleset> allExistingRulesets;
    private List<Ruleset> newRulesets;
    @Getter
    private int numberOfNewRulesets;
    @Getter
    private int totalNumberOfRulesets;

    private List<Docket> allExistingDockets;
    private List<Docket> newDockets;
    @Getter
    private int numberOfNewDockets;
    @Getter
    private int totalNumberOfDockets;

    private List<User> allExistingUsers;
    private List<User> newUsers;
    @Getter
    private int numberOfNewUsers;
    @Getter
    private int totalNumberOfUsers;

    private List<Usergroup> allExistingUserGroups;
    private List<Usergroup> newUserGroups;
    @Getter
    private int numberOfNewUserGroups;
    @Getter
    private int totalNumberOfUserGroups;

    private static Namespace ns = Namespace.getNamespace("http://www.goobi.io/logfile");
    private static final SimpleDateFormat dateConverter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Getter
    @Setter
    private boolean importDockets;
    @Getter
    @Setter
    private boolean importRulesets;

    @Getter
    @Setter
    private boolean importLdaps;
    @Getter
    @Setter
    private boolean importProjects;
    @Getter
    @Setter
    private boolean importUsers;
    @Getter
    @Setter
    private boolean importUserGroups;

    private Rule importRule;

    public void handleFileUpload(FileUploadEvent event) {
        xmlFile = null;
        dockets = null;
        rulesets = null;
        importRule = InfrastructureImportConfiguration.getConfiguredItems();
        try {
            filename = event.getFile().getFileName();
            copyFile(filename, event.getFile().getInputstream());

        } catch (IOException e) {
            log.error(e);
        }
        // extract zip file
        if (filename.endsWith(".zip")) {
            try {
                Path folder = Files.createTempDirectory("import-infrastructure");
                unzip(folder);

                List<Path> extractedElements = StorageProvider.getInstance().listFiles(folder.toString());
                // find xml file and folder
                for (Path p : extractedElements) {
                    if (p.getFileName().toString().endsWith(".xml")) {
                        xmlFile = p;
                    } else if (p.getFileName().toString().equals("dockets")) {
                        dockets = p;
                    } else if (p.getFileName().toString().equals("rulesets")) {
                        rulesets = p;
                    }
                }
            } catch (IOException e) {
                log.error(e);
                Helper.setFehlerMeldung("Cannot read zip file.");
                return;
            }
        } else {
            xmlFile = importFile;
        }
        // read xml file
        loadUploadedFile();

    }

    private void loadUploadedFile() {
        allExistingLdaps = null;
        allExistingDockets = null;
        allExistingProjects = null;
        allExistingRulesets = null;
        allExistingUsers = null;
        allExistingUserGroups = null;

        newLdaps = new ArrayList<>();
        newDockets = new ArrayList<>();
        newProjects = new ArrayList<>();
        newRulesets = new ArrayList<>();
        newUsers = new ArrayList<>();
        newUserGroups = new ArrayList<>();

        // read xml file
        InputStream fis = null;
        BOMInputStream in = null;
        try {
            fis = new FileInputStream(xmlFile.toFile());
            in = new BOMInputStream(fis, false);

            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(in);
            Element infrastructure = document.getRootElement();

            // get information about

            //        ldapGroups
            Element ldaps = infrastructure.getChild("ldaps", ns);
            if (ldaps != null) {
                List<Element> ldapList = ldaps.getChildren("ldap", ns);
                totalNumberOfLdaps = ldapList.size();
                allExistingLdaps = LdapManager.getAllLdapsAsList();
                for (Element ldapElement : ldapList) {
                    Ldap ldap = createLdap(ldapElement);
                    if (ldap != null) {
                        newLdaps.add(ldap);
                    }
                }

            }

            //        projects
            Element projects = infrastructure.getChild("projects", ns);
            if (projects != null) {
                List<Element> projectList = projects.getChildren("project", ns);
                totalNumberOfProjects = projectList.size();
                allExistingProjects = ProjectManager.getAllProjects();
                for (Element projectElement : projectList) {
                    Project project = createProject(projectElement);
                    if (project != null) {
                        newProjects.add(project);
                    }
                }

            }
            //        user
            Element users = infrastructure.getChild("users", ns);
            if (users != null) {
                List<Element> userList = users.getChildren("user", ns);
                totalNumberOfUsers = userList.size();
                allExistingUsers = UserManager.getAllUsers();
                for (Element userElement : userList) {
                    User user = createUser(userElement);
                    if (user != null) {
                        newUsers.add(user);
                    }
                }
            }

            //        rulesets

            Element rulesets = infrastructure.getChild("rulesets", ns);
            if (rulesets != null) {
                List<Element> rulesetList = rulesets.getChildren("ruleset", ns);
                totalNumberOfRulesets = rulesetList.size();
                allExistingRulesets = RulesetManager.getAllRulesets();
                for (Element rulesetElement : rulesetList) {
                    Ruleset ruleset = createRuleset(rulesetElement);
                    if (ruleset != null) {
                        newRulesets.add(ruleset);
                    }
                }
            }

            //        dockets
            Element dockets = infrastructure.getChild("dockets", ns);
            if (dockets != null) {
                List<Element> docketList = dockets.getChildren("docket", ns);
                totalNumberOfDockets = docketList.size();
                allExistingDockets = DocketManager.getAllDockets();
                for (Element docketElement : docketList) {
                    Docket docket = createDocket(docketElement);
                    if (docket != null) {
                        newDockets.add(docket);
                    }
                }
            }

            //        userGroups
            Element userGroups = infrastructure.getChild("userGroups", ns);
            if (userGroups != null) {
                List<Element> usergroupList = userGroups.getChildren("usergroup", ns);
                totalNumberOfUserGroups = usergroupList.size();
                allExistingUserGroups = UsergroupManager.getAllUsergroups();
                for (Element usergroupElement : usergroupList) {
                    Usergroup usergroup = createUsergroup(usergroupElement);
                    if (usergroup != null) {
                        newUserGroups.add(usergroup);
                    }
                }

            }

            numberOfNewDockets = newDockets.size();
            numberOfNewRulesets = newRulesets.size();
            numberOfNewProjects = newProjects.size();
            numberOfNewUsers = newUsers.size();
            numberOfNewLdaps = newLdaps.size();

        } catch (IOException | JDOMException e) {
            log.error(e);
            Helper.setFehlerMeldung(Helper.getTranslation("plugin_kb_invalidFile"));

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }

    }

    private Usergroup createUsergroup(Element usergroupElement) {
        String usergroupname = usergroupElement.getAttributeValue("name");
        UsergroupConfigurationItem ugRule = null;
        // check if usergroup should be overwritten
        if (!importRule.getConfiguredUsergroupRules().isEmpty()) {
            for (UsergroupConfigurationItem pci : importRule.getConfiguredUsergroupRules()) {
                // replace project name, if old project name field is empty or matches the project name in db file
                if (StringUtils.isBlank(pci.getOldUsergroupName()) || pci.getOldUsergroupName().equalsIgnoreCase(usergroupname)) {
                    usergroupname = pci.getNewUsergroupName();
                    ugRule = pci;
                    break;
                }
            }
        }
        // abort, when group already exist
        for (Usergroup ug : allExistingUserGroups) {
            if (ug.getTitel().equals(usergroupname)) {
                return null;
            }
        }
        if (ugRule == null) {
            ugRule = new UsergroupConfigurationItem();
        }

        Usergroup ug = new Usergroup();
        ug.setTitel(usergroupname);
        ug.setBerechtigung(Integer.valueOf(usergroupElement.getAttributeValue("accessLevel")));

        List<String> roles = new ArrayList<>();
        for (Element role : usergroupElement.getChildren("role", ns)) {
            roles.add(getTextFromElement(role));
        }
        // add and remove configured roles
        for (String roleToAdd : ugRule.getAddRoleList()) {
            if (!roles.contains(roleToAdd)) {
                roles.add(roleToAdd);
            }
        }
        for (String roleToRemove : ugRule.getRemoveRoleList()) {
            if (roles.contains(roleToRemove)) {
                roles.remove(roleToRemove);
            }
        }

        ug.setUserRoles(roles);

        Element assignedUsers = usergroupElement.getChild("assignedUsers", ns);
        if (assignedUsers != null && assignedUsers.getChildren() != null) {
            List<User> userList = new ArrayList<>();
            for (Element userElement : assignedUsers.getChildren("user", ns)) {
                String login = userElement.getAttributeValue("login");
                for (User user : allExistingUsers) {
                    if (user.getLogin().equals(login)) {
                        userList.add(user);
                        break;
                    }
                }
                for (User user : newUsers) {
                    if (user.getLogin().equals(login)) {
                        userList.add(user);
                        break;
                    }
                }
            }
            ug.setBenutzer(userList);
        }
        return ug;
    }

    private User createUser(Element userElement) {
        String login = userElement.getAttributeValue("login");
        for (User user : allExistingUsers) {
            if (user.getLogin().equals(login)) {
                return null;
            }
        }

        User user = new User();
        // TODO
        user.setVorname(userElement.getAttributeValue("firstname"));
        user.setNachname(userElement.getAttributeValue("lastname"));
        user.setLogin(userElement.getAttributeValue("login"));
        user.setLdaplogin(userElement.getAttributeValue("ldaplogin"));
        user.setIstAktiv(Boolean.parseBoolean(userElement.getAttributeValue("active")));
        user.setIsVisible(userElement.getAttributeValue("visible"));
        user.setStandort(userElement.getAttributeValue("place"));
        user.setTabellengroesse(Integer.parseInt(userElement.getAttributeValue("tablesize")));
        user.setSessiontimeout(Integer.parseInt(userElement.getAttributeValue("sessionlength")));
        user.setMetadatenSprache(userElement.getAttributeValue("metadatalanguage"));
        user.setMitMassendownload(Boolean.parseBoolean(userElement.getAttributeValue("massdownload")));

        String ldapGroup = userElement.getAttributeValue("ldapgroup");
        for (Ldap grp : allExistingLdaps) {
            if (grp.getTitel().equals(ldapGroup)) {
                user.setLdapGruppe(grp);
                break;
            }
        }
        if (user.getLdapGruppe() == null) {
            for (Ldap grp : newLdaps) {
                if (grp.getTitel().equals(ldapGroup)) {
                    user.setLdapGruppe(grp);
                    break;
                }
            }
        }

        user.setCss(userElement.getAttributeValue("css"));
        user.setEmail(userElement.getAttributeValue("email"));
        user.setShortcutPrefix(userElement.getAttributeValue("shortcut"));
        user.setEncryptedPassword(userElement.getAttributeValue("password"));
        user.setPasswordSalt(userElement.getAttributeValue("salt"));

        if (StringUtils.isBlank(user.getEncryptedPassword())) {
            createNewPassword(user);
        }

        user.setDisplayDeactivatedProjects(Boolean.parseBoolean(userElement.getAttributeValue("displayDeactivatedProjects")));
        user.setDisplayFinishedProcesses(Boolean.parseBoolean(userElement.getAttributeValue("displayFinishedProcesses")));
        user.setDisplaySelectBoxes(Boolean.parseBoolean(userElement.getAttributeValue("displaySelectBoxes")));
        user.setDisplayIdColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayIdColumn")));
        user.setDisplayBatchColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayBatchColumn")));
        user.setDisplayProcessDateColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayProcessDateColumn")));
        user.setDisplayLocksColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayLocksColumn")));
        user.setDisplaySwappingColumn(Boolean.parseBoolean(userElement.getAttributeValue("displaySwappingColumn")));
        user.setDisplayModulesColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayModulesColumn")));
        user.setDisplayMetadataColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayMetadataColumn")));
        user.setDisplayThumbColumn(Boolean.parseBoolean(userElement.getAttributeValue("displayThumbColumn")));
        user.setDisplayGridView(Boolean.parseBoolean(userElement.getAttributeValue("displayGridView")));
        user.setDisplayAutomaticTasks(Boolean.parseBoolean(userElement.getAttributeValue("displayAutomaticTasks")));
        user.setHideCorrectionTasks(Boolean.parseBoolean(userElement.getAttributeValue("hideCorrectionTasks")));
        user.setDisplayOnlySelectedTasks(Boolean.parseBoolean(userElement.getAttributeValue("displayOnlySelectedTasks")));
        user.setDisplayOnlyOpenTasks(Boolean.parseBoolean(userElement.getAttributeValue("displayOnlyOpenTasks")));
        user.setDisplayOtherTasks(Boolean.parseBoolean(userElement.getAttributeValue("displayOtherTasks")));
        user.setMetsDisplayTitle(Boolean.parseBoolean(userElement.getAttributeValue("metsDisplayTitle")));
        user.setMetsLinkImage(Boolean.parseBoolean(userElement.getAttributeValue("metsLinkImage")));
        user.setMetsDisplayPageAssignments(Boolean.parseBoolean(userElement.getAttributeValue("metsDisplayPageAssignments")));
        user.setMetsDisplayHierarchy(Boolean.parseBoolean(userElement.getAttributeValue("metsDisplayHierarchy")));
        user.setMetsDisplayProcessID(Boolean.parseBoolean(userElement.getAttributeValue("metsDisplayProcessID")));
        user.setMetsEditorTime(Integer.parseInt(userElement.getAttributeValue("metsEditorTime")));
        user.setCustomColumns(userElement.getAttributeValue("customColumns"));
        user.setCustomCss(userElement.getAttributeValue("customCss"));

        Element assignedProjects = userElement.getChild("assignedProjects", ns);
        if (assignedProjects != null && assignedProjects.getChildren() != null) {
            List<Project> projectList = new ArrayList<>();
            for (Element projectElement : assignedProjects.getChildren("project", ns)) {
                String title = projectElement.getAttributeValue("title");
                for (Project project : allExistingProjects) {
                    if (project.getTitel().equals(title)) {
                        projectList.add(project);
                        break;
                    }
                }
                for (Project project : newProjects) {
                    if (project.getTitel().equals(title)) {
                        projectList.add(project);
                        break;
                    }
                }
            }
            user.setProjekte(projectList);
        }

        return user;
    }

    private Ruleset createRuleset(Element rulesetElement) {
        String rulesetName = rulesetElement.getAttributeValue("name");
        String filename = rulesetElement.getAttributeValue("file");

        // check if ruleset should be overwritten
        if (!importRule.getConfiguredRulesetRules().isEmpty()) {
            for (RulesetConfigurationItem rci : importRule.getConfiguredRulesetRules()) {
                // replace ruleset name, if old ruleset name field is empty or matches the ruleset name in db file
                if (StringUtils.isBlank(rci.getOldRulesetName()) || rci.getOldRulesetName().equalsIgnoreCase(rulesetName)) {
                    if (StringUtils.isNotBlank(rci.getNewRulesetName())) {
                        rulesetName = rci.getNewRulesetName();
                    }
                    if (StringUtils.isNotBlank(rci.getNewFileName())) {
                        filename = rci.getNewFileName();
                    }
                    break;
                }
            }
        }

        for (Ruleset ruleset : allExistingRulesets) {
            if (ruleset.getTitel().equals(rulesetName) && ruleset.getDatei().equals(filename)) {
                return null;
            }
        }
        Ruleset ruleset = new Ruleset();
        ruleset.setDatei(filename);
        ruleset.setTitel(rulesetName);
        return ruleset;
    }

    private Docket createDocket(Element docketElement) {
        String docketName = docketElement.getAttributeValue("name");
        String filename = docketElement.getAttributeValue("file");

        // check if configured rule applies
        if (!importRule.getConfiguredDocketRules().isEmpty()) {
            for (DocketConfigurationItem dci : importRule.getConfiguredDocketRules()) {
                // replace docket, if old name field is empty or matches the name in db file
                if (StringUtils.isBlank(dci.getOldDocketName()) || dci.getOldDocketName().equalsIgnoreCase(docketName)) {
                    if (StringUtils.isNotBlank(dci.getNewDocketName())) {
                        docketName = dci.getNewDocketName();
                    }
                    if (StringUtils.isNotBlank(dci.getNewFileName())) {
                        filename = dci.getNewFileName();
                    }
                    break;
                }
            }
        }

        for (Docket docket : allExistingDockets) {
            if (docket.getName().equals(docketName) && docket.getFile().equals(filename)) {
                return null;
            }
        }
        Docket docket = new Docket();
        docket.setFile(filename);
        docket.setName(docketName);
        return docket;
    }

    private Project createProject(Element projectElement) {

        String projectTitle = projectElement.getChild("title", ns).getText();
        ProjectConfigurationItem projectRule = null;
        // check if project should be overwritten
        if (!importRule.getConfiguredProjectRules().isEmpty()) {
            for (ProjectConfigurationItem pci : importRule.getConfiguredProjectRules()) {
                // replace project name, if old project name field is empty or matches the project name in db file
                if (StringUtils.isBlank(pci.getOldProjectName()) || pci.getOldProjectName().equalsIgnoreCase(projectTitle)) {
                    projectTitle = pci.getNewProjectName();
                    projectRule = pci;
                    break;
                }
            }
        }

        for (Project project : allExistingProjects) {
            if (project.getTitel().equals(projectTitle)) {
                // found existing project
                return null;
            }
        }
        // create new project
        if (projectRule == null) {
            projectRule = new ProjectConfigurationItem();
        }

        Project project = new Project();
        project = new Project();
        project.setTitel(projectTitle);
        project.setProjectIsArchived(Boolean.parseBoolean(projectElement.getAttributeValue("archived")));

        project.setFileFormatInternal(StringUtils.isNotBlank(projectRule.getFileFormatInternal()) ? projectRule.getFileFormatInternal()
                : projectElement.getChild("fileFormatInternal", ns).getText());
        project.setFileFormatDmsExport(StringUtils.isNotBlank(projectRule.getFileFormatDmsExport()) ? projectRule.getFileFormatDmsExport()
                : projectElement.getChild("fileFormatDmsExport", ns).getText());

        Element startDateElement = projectElement.getChild("startDate", ns);
        Element endDateElement = projectElement.getChild("endDate", ns);

        if (startDateElement != null && StringUtils.isNotBlank(startDateElement.getText())) {
            try {
                project.setStartDate(dateConverter.parse(startDateElement.getText()));
            } catch (ParseException e) {
                log.error(e);
            }
        }

        if (endDateElement != null && StringUtils.isNotBlank(endDateElement.getText())) {
            try {
                project.setEndDate(dateConverter.parse(endDateElement.getText()));
            } catch (ParseException e) {
                log.error(e);
            }
        }
        Element pages = projectElement.getChild("pages", ns);
        Element volumes = projectElement.getChild("volumes", ns);
        if (StringUtils.isNotBlank(pages.getText())) {
            project.setNumberOfPages(Integer.parseInt(pages.getText()));
        }
        if (StringUtils.isNotBlank(volumes.getText())) {
            project.setNumberOfVolumes(Integer.parseInt(volumes.getText()));
        }

        // export configuration
        Element exportConfiguration = projectElement.getChild("exportConfiguration", ns);
        project.setUseDmsImport(projectRule.getUseDmsImport() != null ? projectRule.getUseDmsImport() : Boolean.parseBoolean(exportConfiguration
                .getAttributeValue("useDmsImport")));
        project.setDmsImportCreateProcessFolder(projectRule.getDmsImportCreateProcessFolder() != null ? projectRule.getDmsImportCreateProcessFolder()
                : Boolean.parseBoolean(exportConfiguration.getAttributeValue("dmsImportCreateProcessFolder")));

        Element dmsImportTimeOut = exportConfiguration.getChild("dmsImportTimeOut", ns);
        if (projectRule.getDmsImportTimeOut() != null) {
            dmsImportTimeOut.setText("" + projectRule.getDmsImportTimeOut());
        }

        if (StringUtils.isNotBlank(dmsImportTimeOut.getText())) {
            project.setDmsImportTimeOut(Integer.parseInt(dmsImportTimeOut.getText()));
        }
        project.setDmsImportRootPath(StringUtils.isNotBlank(projectRule.getDmsImportRootPath()) ? projectRule.getDmsImportRootPath()
                : getTextFromElement(exportConfiguration.getChild("dmsImportRootPath", ns)));
        project.setDmsImportImagesPath(StringUtils.isNotBlank(projectRule.getDmsImportImagesPath()) ? projectRule.getDmsImportImagesPath()
                : getTextFromElement(exportConfiguration.getChild("dmsImportImagesPath", ns)));
        project.setDmsImportSuccessPath(StringUtils.isNotBlank(projectRule.getDmsImportSuccessPath()) ? projectRule.getDmsImportSuccessPath()
                : getTextFromElement(exportConfiguration.getChild("dmsImportSuccessPath", ns)));
        project.setDmsImportErrorPath(StringUtils.isNotBlank(projectRule.getDmsImportErrorPath()) ? projectRule.getDmsImportErrorPath()
                : getTextFromElement(exportConfiguration.getChild("dmsImportErrorPath", ns)));

        // mets configuration
        Element metsConfiguration = projectElement.getChild("metsConfiguration", ns);
        project.setMetsRightsOwnerLogo(StringUtils.isNotBlank(projectRule.getMetsRightsOwnerLogo()) ? projectRule.getMetsRightsOwnerLogo()
                : getTextFromElement(metsConfiguration.getChild("metsRightsOwnerLogo", ns)));
        project.setMetsRightsOwnerSite(StringUtils.isNotBlank(projectRule.getMetsRightsOwnerSite()) ? projectRule.getMetsRightsOwnerSite()
                : getTextFromElement(metsConfiguration.getChild("metsRightsOwnerSite", ns)));
        project.setMetsRightsOwnerMail(StringUtils.isNotBlank(projectRule.getMetsRightsOwnerMail()) ? projectRule.getMetsRightsOwnerMail()
                : getTextFromElement(metsConfiguration.getChild("metsRightsOwnerMail", ns)));
        project.setMetsDigiprovReference(StringUtils.isNotBlank(projectRule.getMetsDigiprovReference()) ? projectRule.getMetsDigiprovReference()
                : getTextFromElement(metsConfiguration.getChild("metsDigiprovReference", ns)));
        project.setMetsDigiprovPresentation(StringUtils.isNotBlank(projectRule.getMetsDigiprovPresentation()) ? projectRule
                .getMetsDigiprovPresentation() : getTextFromElement(metsConfiguration.getChild("metsDigiprovPresentation", ns)));
        project.setMetsDigiprovReferenceAnchor(StringUtils.isNotBlank(projectRule.getMetsDigiprovReferenceAnchor()) ? projectRule
                .getMetsDigiprovReferenceAnchor() : getTextFromElement(metsConfiguration.getChild("metsDigiprovReferenceAnchor", ns)));
        project.setMetsPointerPath(StringUtils.isNotBlank(projectRule.getMetsPointerPath()) ? projectRule.getMetsPointerPath() : getTextFromElement(
                metsConfiguration.getChild("metsPointerPath", ns)));
        project.setMetsPointerPathAnchor(StringUtils.isNotBlank(projectRule.getMetsPointerPathAnchor()) ? projectRule.getMetsPointerPathAnchor()
                : getTextFromElement(metsConfiguration.getChild("metsPointerPathAnchor", ns)));
        project.setMetsPurl(StringUtils.isNotBlank(projectRule.getMetsPurl()) ? projectRule.getMetsPurl() : getTextFromElement(metsConfiguration
                .getChild("metsPurl", ns)));
        project.setMetsContentIDs(StringUtils.isNotBlank(projectRule.getMetsContentIDs()) ? projectRule.getMetsContentIDs() : getTextFromElement(
                metsConfiguration.getChild("metsContentIDs", ns)));
        project.setMetsRightsSponsor(StringUtils.isNotBlank(projectRule.getMetsRightsSponsor()) ? projectRule.getMetsRightsSponsor()
                : getTextFromElement(metsConfiguration.getChild("metsRightsSponsor", ns)));
        project.setMetsRightsSponsorLogo(StringUtils.isNotBlank(projectRule.getMetsRightsSponsorLogo()) ? projectRule.getMetsRightsSponsorLogo()
                : getTextFromElement(metsConfiguration.getChild("metsRightsSponsorLogo", ns)));
        project.setMetsRightsSponsorSiteURL(StringUtils.isNotBlank(projectRule.getMetsRightsSponsorSiteURL()) ? projectRule
                .getMetsRightsSponsorSiteURL() : getTextFromElement(metsConfiguration.getChild("metsRightsSponsorSiteURL", ns)));
        project.setMetsRightsLicense(StringUtils.isNotBlank(projectRule.getMetsRightsLicense()) ? projectRule.getMetsRightsLicense()
                : getTextFromElement(metsConfiguration.getChild("metsRightsLicense", ns)));

        // filegroups
        Element fileGroups = projectElement.getChild("fileGroups", ns);
        if (fileGroups != null) {
            for (Element grpElement : fileGroups.getChildren()) {
                String fileGroupName = grpElement.getAttributeValue("name");
                String folder = grpElement.getAttributeValue("folder");
                String mimetype = grpElement.getAttributeValue("mimetype");
                String path = grpElement.getAttributeValue("path");
                String suffix = grpElement.getAttributeValue("suffix");
                // check if a rule exists
                for (ProjectConfigurationItem.FilegroupConfigurationItem item : projectRule.getFilegroups()) {
                    if (fileGroupName.equals(item.getOldName())) {
                        if (StringUtils.isNotBlank(item.getNewName())) {
                            fileGroupName = item.getNewName();
                        }

                        if (StringUtils.isNotBlank(item.getPath())) {
                            path = item.getPath();
                        }

                        if (StringUtils.isNotBlank(item.getFileSuffix())) {
                            suffix = item.getFileSuffix();
                        }
                        if (StringUtils.isNotBlank(item.getMimeType())) {
                            mimetype = item.getMimeType();
                        }
                        if (StringUtils.isNotBlank(item.getFolderValidation())) {
                            folder = item.getFolderValidation();
                        }
                        break;
                    }
                }

                ProjectFileGroup pfg = new ProjectFileGroup();
                pfg.setFolder(folder);
                pfg.setMimetype(mimetype);
                pfg.setName(fileGroupName);
                pfg.setPath(path);
                pfg.setProject(project);
                pfg.setSuffix(suffix);

                project.getFilegroups().add(pfg);
            }
        }

        return project;
    }

    private Ldap createLdap(Element ldapElement) {
        // check if ldap already exist, otherwise create new ldap
        String ldapName = ldapElement.getAttributeValue("title");
        String dn = ldapElement.getAttributeValue("dn");

        LdapConfigurationItem ldapRule = null;
        // check if project should be overwritten
        if (!importRule.getConfiguredLdapRules().isEmpty()) {
            for (LdapConfigurationItem lci : importRule.getConfiguredLdapRules()) {
                // replace project name, if old project name field is empty or matches the project name in db file
                if (StringUtils.isBlank(lci.getOldLadapName()) || lci.getOldLadapName().equalsIgnoreCase(ldapName)) {
                    ldapName = lci.getNewLdapName();
                    ldapRule = lci;
                    break;
                }
            }
        }

        for (Ldap ldap : allExistingLdaps) {
            if (ldap.getTitel().equals(ldapName) && ldap.getUserDN().equals(dn)) {
                // found existing ldap
                return null;
            }
        }

        if (ldapRule == null) {
            ldapRule = new LdapConfigurationItem();
        }

        // otherwise create new one

        Ldap ldap = new Ldap();
        ldap.setTitel(ldapElement.getAttributeValue("title"));
        ldap.setHomeDirectory(StringUtils.isNotBlank(ldapRule.getHomeDirectory()) ? ldapRule.getHomeDirectory() : ldapElement.getAttributeValue(
                "homeDirectory"));
        ldap.setGidNumber(StringUtils.isNotBlank(ldapRule.getGidNumber()) ? ldapRule.getGidNumber() : ldapElement.getAttributeValue("gidNumber"));
        ldap.setUserDN(StringUtils.isNotBlank(ldapRule.getDn()) ? ldapRule.getDn() : ldapElement.getAttributeValue("dn"));
        ldap.setObjectClasses(StringUtils.isNotBlank(ldapRule.getObjectClass()) ? ldapRule.getObjectClass() : ldapElement.getAttributeValue(
                "objectClass"));
        ldap.setSambaSID(StringUtils.isNotBlank(ldapRule.getSn()) ? ldapRule.getSn() : ldapElement.getAttributeValue("sambaSID"));
        ldap.setSn(StringUtils.isNotBlank(ldapRule.getGidNumber()) ? ldapRule.getGidNumber() : ldapElement.getAttributeValue("sn"));
        ldap.setUid(StringUtils.isNotBlank(ldapRule.getUid()) ? ldapRule.getUid() : ldapElement.getAttributeValue("uid"));
        ldap.setDescription(StringUtils.isNotBlank(ldapRule.getDescription()) ? ldapRule.getDescription() : ldapElement.getAttributeValue(
                "description"));
        ldap.setDisplayName(StringUtils.isNotBlank(ldapRule.getDisplayName()) ? ldapRule.getDisplayName() : ldapElement.getAttributeValue(
                "displayName"));
        ldap.setGecos(StringUtils.isNotBlank(ldapRule.getGecos()) ? ldapRule.getGecos() : ldapElement.getAttributeValue("gecos"));
        ldap.setLoginShell(StringUtils.isNotBlank(ldapRule.getLoginShell()) ? ldapRule.getLoginShell() : ldapElement.getAttributeValue("loginShell"));
        ldap.setSambaAcctFlags(StringUtils.isNotBlank(ldapRule.getSambaAcctFlags()) ? ldapRule.getSambaAcctFlags() : ldapElement.getAttributeValue(
                "sambaAcctFlags"));
        ldap.setSambaLogonScript(StringUtils.isNotBlank(ldapRule.getSambaLogonScript()) ? ldapRule.getSambaLogonScript() : ldapElement
                .getAttributeValue("sambaLogonScript"));
        ldap.setSambaPwdMustChange(StringUtils.isNotBlank(ldapRule.getSambaPwdMustChange()) ? ldapRule.getSambaPwdMustChange() : ldapElement
                .getAttributeValue("sambaPwdMustChange"));
        ldap.setSambaPasswordHistory(StringUtils.isNotBlank(ldapRule.getSambaPasswordHistory()) ? ldapRule.getSambaPasswordHistory() : ldapElement
                .getAttributeValue("sambaPasswordHistory"));
        ldap.setSambaLogonHours(StringUtils.isNotBlank(ldapRule.getSambaLogonHours()) ? ldapRule.getSambaLogonHours() : ldapElement.getAttributeValue(
                "sambaLogonHours"));
        ldap.setSambaKickoffTime(StringUtils.isNotBlank(ldapRule.getSambaKickoffTime()) ? ldapRule.getSambaKickoffTime() : ldapElement
                .getAttributeValue("sambaKickoffTime"));

        return ldap;
    }

    private void copyFile(String fileName, InputStream in) {
        OutputStream out = null;

        try {
            String extension = fileName.substring(fileName.indexOf("."));

            importFile = Files.createTempFile(fileName, extension);
            out = new FileOutputStream(importFile.toFile());

            int read = 0;
            byte[] bytes = new byte[1024];

            while ((read = in.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
        } catch (IOException e) {
            log.error(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }

        }
    }

    public void unzip(Path destDir) throws IOException {
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }

        try (FileSystem zipFileSystem = FileSystems.newFileSystem(importFile, null)) {
            final Path root = zipFileSystem.getRootDirectories().iterator().next();

            walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final Path destFile = Paths.get(destDir.toString(), file.toString());
                    try {
                        copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (DirectoryNotEmptyException ignore) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    final Path dirToCreate = Paths.get(destDir.toString(), dir.toString());
                    if (!Files.exists(dirToCreate)) {
                        Files.createDirectories(dirToCreate);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public void importSelectedData() {
        // import dockets if checkbox is checked and new dockets are found
        if (!newDockets.isEmpty() && importDockets) {
            for (Docket docket : newDockets) {
                try {
                    // save docket
                    DocketManager.saveDocket(docket);
                    // copy xsl file
                    if (dockets != null) {
                        Path docketToImport = Paths.get(dockets.toString(), docket.getFile());
                        Path destination = Paths.get(ConfigurationHelper.getInstance().getXsltFolder(), docket.getFile());
                        if (StorageProvider.getInstance().isFileExists(docketToImport) && !StorageProvider.getInstance().isFileExists(destination)) {
                            StorageProvider.getInstance().copyFile(docketToImport, destination);
                        }
                    }
                } catch (DAOException | IOException e) {
                    log.error(e);
                }
            }
        }

        if (!newRulesets.isEmpty() && importRulesets) {
            for (Ruleset ruleset : newRulesets) {
                try {
                    // save ruleset
                    RulesetManager.saveRuleset(ruleset);
                    // copy ruleset file
                    if (rulesets != null) {
                        Path rulesetToImport = Paths.get(rulesets.toString(), ruleset.getDatei());
                        Path destination = Paths.get(ConfigurationHelper.getInstance().getRulesetFolder(), ruleset.getDatei());
                        if (StorageProvider.getInstance().isFileExists(rulesetToImport) && !StorageProvider.getInstance().isFileExists(destination)) {
                            StorageProvider.getInstance().copyFile(rulesetToImport, destination);
                        }
                    }
                } catch (DAOException | IOException e) {
                    log.error(e);
                }
            }
        }

        if (!newLdaps.isEmpty() && importLdaps) {
            for (Ldap ldap : newLdaps) {
                try {
                    LdapManager.saveLdap(ldap);
                } catch (DAOException e) {
                    log.error(e);
                }
            }
        }

        if (!newProjects.isEmpty() && importProjects) {
            for (Project project : newProjects) {
                try {
                    ProjectManager.saveProject(project);
                } catch (DAOException e) {
                    log.error(e);
                }
            }
        }

        if (!newUsers.isEmpty() && importUsers) {
            for (User user : newUsers) {
                try {
                    UserManager.saveUser(user);
                } catch (DAOException e) {
                    log.error(e);
                }
            }
        }

        if (!newUserGroups.isEmpty() && importUserGroups) {
            for (Usergroup ug : newUserGroups) {
                try {
                    UsergroupManager.saveUsergroup(ug);

                } catch (DAOException e) {
                    log.error(e);
                }
            }
        }
    }

    private String getTextFromElement(Element element) {
        if (element != null && element.getText() != null) {
            return element.getText();
        } else {
            return "";
        }
    }

    private void createNewPassword(User user) {

        RandomNumberGenerator rng = new SecureRandomNumberGenerator();

        Object salt = rng.nextBytes();
        user.setPasswordSalt(salt.toString());
        String password = rng.nextBytes().toString();
        user.setEncryptedPassword(user.getPasswordHash(password));
        user.setPasswort("");
    }
}
