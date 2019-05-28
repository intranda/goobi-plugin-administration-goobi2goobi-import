package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

import de.sub.goobi.config.ConfigPlugins;

public class InfrastructureImportConfiguration {

    private static Rule rule;
    private static XMLConfiguration config = null;

    @SuppressWarnings("unchecked")
    public static Rule getConfiguredItems() {
        if (rule == null) {
            if (config == null) {
                loadConfig();
            }
            rule = new Rule();

            // read docket configuration
            List<HierarchicalConfiguration> docketList = config.configurationsAt("/config/docket");
            List<DocketConfigurationItem> configuredDocketRules = new ArrayList<>();
            rule.setConfiguredDocketRules(configuredDocketRules);

            if (docketList != null && !docketList.isEmpty()) {
                for (HierarchicalConfiguration docketConfiguration : docketList) {

                    String oldDocketName = docketConfiguration.getString("./@name", null);
                    String newDocketName = docketConfiguration.getString("./newDocketName", null);
                    String newFileName = docketConfiguration.getString("./newFileName", null);

                    DocketConfigurationItem dci = new DocketConfigurationItem();
                    dci.setOldDocketName(oldDocketName);
                    dci.setNewDocketName(newDocketName);
                    dci.setNewFileName(newFileName);

                    configuredDocketRules.add(dci);
                }
            }

            // read ruleset configuration
            List<HierarchicalConfiguration> rulesetList = config.configurationsAt("/config/ruleset");
            List<RulesetConfigurationItem> configuredRulesetRules = new ArrayList<>();
            rule.setConfiguredRulesetRules(configuredRulesetRules);
            if (rulesetList != null && !rulesetList.isEmpty()) {
                for (HierarchicalConfiguration rulesetConfiguration : rulesetList) {
                    RulesetConfigurationItem rci = new RulesetConfigurationItem();
                    rci.setOldRulesetName(rulesetConfiguration.getString("./@name", null));
                    rci.setNewRulesetName(rulesetConfiguration.getString("./newRulesetName", null));
                    rci.setNewFileName(rulesetConfiguration.getString("./newFileName", null));

                    configuredRulesetRules.add(rci);
                }
            }

            // read project configuration
            List<HierarchicalConfiguration> projectList = config.configurationsAt("/config/project");
            List<ProjectConfigurationItem> configuredProjectRules = new ArrayList<>();
            rule.setConfiguredProjectRules(configuredProjectRules);

            if (projectList != null && !projectList.isEmpty()) {
                for (HierarchicalConfiguration projectConfig : projectList) {
                    String oldProjectName = projectConfig.getString("./@name", null);
                    String newProjectName = projectConfig.getString("./newProjectName", null);
                    ProjectConfigurationItem pci = new ProjectConfigurationItem();
                    pci.setOldProjectName(oldProjectName);
                    pci.setNewProjectName(newProjectName);
                    configuredProjectRules.add(pci);

                    List<ProjectConfigurationItem.FilegroupConfigurationItem> configuredFilegroups = new ArrayList<>();
                    pci.setFilegroups(configuredFilegroups);

                    List<HierarchicalConfiguration> filegroups = projectConfig.configurationsAt("/filegroup");
                    if (filegroups != null && !filegroups.isEmpty()) {
                        for (HierarchicalConfiguration filegroup : filegroups) {
                            String oldFilegroupName = filegroup.getString("./@name", null);
                            String newFilegroupName = filegroup.getString("./newFilegroupName", null);
                            String path = filegroup.getString("./path", null);
                            String mimeType = filegroup.getString("./mimeType", null);
                            String fileSuffix = filegroup.getString("./fileSuffix", null);
                            String folderValidation = filegroup.getString("./folderValidation", null);

                            ProjectConfigurationItem.FilegroupConfigurationItem fg = pci.createNewFilgroupItem();
                            fg.setOldName(oldFilegroupName);
                            fg.setNewName(newFilegroupName);
                            fg.setPath(path);
                            fg.setMimeType(mimeType);
                            fg.setFileSuffix(fileSuffix);
                            fg.setFolderValidation(folderValidation);
                            configuredFilegroups.add(fg);

                        }
                    }
                    pci.setFileFormatInternal(projectConfig.getString("./fileFormatInternal", null));
                    pci.setFileFormatDmsExport(projectConfig.getString("./fileFormatDmsExport", null));

                    // exportConfiguration
                    HierarchicalConfiguration exportConfiguration = projectConfig.configurationAt("/exportConfiguration");
                    if (exportConfiguration != null) {
                        pci.setUseDmsImport(exportConfiguration.getBoolean("./@useDmsImport", null));
                        pci.setDmsImportCreateProcessFolder(exportConfiguration.getBoolean("./@dmsImportCreateProcessFolder", null));
                        pci.setDmsImportTimeOut(exportConfiguration.getInteger("./@dmsImportTimeOut", null));
                        pci.setDmsImportRootPath(exportConfiguration.getString("./@dmsImportRootPath", null));
                        pci.setDmsImportImagesPath(exportConfiguration.getString("./@dmsImportImagesPath", null));
                        pci.setDmsImportSuccessPath(exportConfiguration.getString("./@dmsImportSuccessPath", null));
                        pci.setDmsImportErrorPath(exportConfiguration.getString("./@dmsImportErrorPath", null));
                    }

                    // metsConfiguration
                    HierarchicalConfiguration metsConfiguration = projectConfig.configurationAt("/metsConfiguration");
                    if (metsConfiguration != null) {
                        pci.setMetsRightsOwnerLogo(metsConfiguration.getString("./@metsRightsOwnerLogo", null));
                        pci.setMetsRightsOwnerSite(metsConfiguration.getString("./@metsRightsOwnerSite", null));
                        pci.setMetsRightsOwnerMail(metsConfiguration.getString("./@metsRightsOwnerMail", null));
                        pci.setMetsDigiprovReference(metsConfiguration.getString("./@metsDigiprovReference", null));
                        pci.setMetsDigiprovPresentation(metsConfiguration.getString("./@metsDigiprovPresentation", null));
                        pci.setMetsDigiprovReferenceAnchor(metsConfiguration.getString("./@metsDigiprovReferenceAnchor", null));
                        pci.setMetsPointerPath(metsConfiguration.getString("./@metsPointerPath", null));
                        pci.setMetsPointerPathAnchor(metsConfiguration.getString("./@metsPointerPathAnchor", null));
                        pci.setMetsPurl(metsConfiguration.getString("./@metsPurl", null));
                        pci.setMetsContentIDs(metsConfiguration.getString("./@metsContentIDs", null));
                        pci.setMetsRightsSponsor(metsConfiguration.getString("./@metsRightsSponsor", null));
                        pci.setMetsRightsSponsorLogo(metsConfiguration.getString("./@metsRightsSponsorLogo", null));
                        pci.setMetsRightsSponsorSiteURL(metsConfiguration.getString("./@metsRightsSponsorSiteURL", null));
                        pci.setMetsRightsLicense(metsConfiguration.getString("./@metsRightsLicense", null));
                    }

                }

                // read ldap configuration
                List<HierarchicalConfiguration> ldapList = config.configurationsAt("/config/ldap");
                List<LdapConfigurationItem> configuredLdapRules = new ArrayList<>();
                rule.setConfiguredLdapRules(configuredLdapRules);

                if (ldapList != null && !ldapList.isEmpty()) {
                    for (HierarchicalConfiguration ldapConfiguration : ldapList) {
                        LdapConfigurationItem rci = new LdapConfigurationItem();
                        rci.setOldLadapName(ldapConfiguration.getString("./@name", null));
                        rci.setNewLdapName(ldapConfiguration.getString("./newRulesetName", null));
                        HierarchicalConfiguration configuration = ldapConfiguration.configurationAt("./ldapConfiguration");
                        if (configuration != null) {
                            rci.setHomeDirectory(configuration.getString("./@homeDirectory", null));
                            rci.setGidNumber(configuration.getString("./@gidNumber", null));
                            rci.setDn(configuration.getString("./@dn", null));
                            rci.setObjectClass(configuration.getString("./@objectClass", null));
                            rci.setSambaSID(configuration.getString("./@sambaSID", null));
                            rci.setSn(configuration.getString("./@sn", null));
                            rci.setUid(configuration.getString("./@uid", null));
                            rci.setDescription(configuration.getString("./@description", null));
                            rci.setDisplayName(configuration.getString("./@displayName", null));
                            rci.setGecos(configuration.getString("./@gecos", null));
                            rci.setLoginShell(configuration.getString("./@loginShell", null));
                            rci.setSambaAcctFlags(configuration.getString("./@sambaAcctFlags", null));
                            rci.setSambaLogonScript(configuration.getString("./@sambaLogonScript", null));
                            rci.setSambaPrimaryGroupSID(configuration.getString("./@sambaPrimaryGroupSID", null));
                            rci.setSambaPwdMustChange(configuration.getString("./@sambaPwdMustChange", null));
                            rci.setSambaPasswordHistory(configuration.getString("./@sambaPasswordHistory", null));
                            rci.setSambaLogonHours(configuration.getString("./@sambaLogonHours", null));
                            rci.setSambaKickoffTime(configuration.getString("./@sambaKickoffTime", null));
                        }
                        configuredLdapRules.add(rci);
                    }
                }


                // read ldap configuration
                List<HierarchicalConfiguration> usergroupList = config.configurationsAt("/config/usergroup");
                List<UsergroupConfigurationItem> configuredUsergroupRules = new ArrayList<>();
                rule.setConfiguredUsergroupRules(configuredUsergroupRules);

                if (usergroupList != null && !usergroupList.isEmpty()) {
                    for (HierarchicalConfiguration ugConfiguration : usergroupList) {
                        UsergroupConfigurationItem rci = new UsergroupConfigurationItem();
                        rci.setOldUsergroupName(ugConfiguration.getString("./@name", null));
                        rci.setNewUsergroupName(ugConfiguration.getString("./newRulesetName", null));
                        List<String> rolesToAdd = ugConfiguration.getList("./addRole");
                        List<String> rolesToRemove = ugConfiguration.getList("./removeRole");
                        rci.setAddRoleList(rolesToAdd);
                        rci.setRemoveRoleList(rolesToRemove);
                        configuredUsergroupRules.add(rci);
                    }
                }
            }
        }
        return rule;
    }

    public static void resetRule() {
        rule = null;
    }

    private static void loadConfig() {
        config = ConfigPlugins.getPluginConfig("goobi-plugin-administration-upload-infrastructure");
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        config.setExpressionEngine(new XPathExpressionEngine());
    }
}
