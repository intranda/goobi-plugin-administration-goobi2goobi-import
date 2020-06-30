package org.goobi.api.mq.ticket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.lang.StringUtils;
import org.apache.oro.text.perl.Perl5Util;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketHandler;
import org.goobi.beans.Batch;
import org.goobi.beans.Docket;
import org.goobi.beans.Institution;
import org.goobi.beans.LogEntry;
import org.goobi.beans.Masterpiece;
import org.goobi.beans.Masterpieceproperty;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.beans.Template;
import org.goobi.beans.Templateproperty;
import org.goobi.beans.User;
import org.goobi.beans.Usergroup;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginReturnValue;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import de.intranda.goobi.importrules.DocketConfigurationItem;
import de.intranda.goobi.importrules.MetadataConfigurationItem;
import de.intranda.goobi.importrules.ProcessImportConfiguration;
import de.intranda.goobi.importrules.ProjectConfigurationItem;
import de.intranda.goobi.importrules.PropertyConfigurationItem;
import de.intranda.goobi.importrules.Rule;
import de.intranda.goobi.importrules.RulesetConfigurationItem;
import de.intranda.goobi.importrules.StepConfigurationItem;
import de.intranda.goobi.importrules.StepConfigurationItem.ConfigurationType;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.helper.S3FileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.DocketManager;
import de.sub.goobi.persistence.managers.InstitutionManager;
import de.sub.goobi.persistence.managers.MasterpieceManager;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.MySQLHelper;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import de.sub.goobi.persistence.managers.RulesetManager;
import de.sub.goobi.persistence.managers.StepManager;
import de.sub.goobi.persistence.managers.TemplateManager;
import de.sub.goobi.persistence.managers.UserManager;
import de.sub.goobi.persistence.managers.UsergroupManager;
import lombok.extern.log4j.Log4j;

@Log4j
public class DatabaseInformationTicket extends ExportDms implements TicketHandler<PluginReturnValue> {

    // xml conversion
    private static final Namespace goobiNamespace = Namespace.getNamespace("http://www.goobi.io/logfile");
    private static final SimpleDateFormat dateConverter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    // xml namespaces
    private static final Namespace goobi = Namespace.getNamespace("goobi", "http://meta.goobi.org/v1.5.1/");
    private static final Namespace mets = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
    private static final Namespace mods = Namespace.getNamespace("mods", "http://www.loc.gov/mods/v3");

    // sql statements
    private static final String processIdCheckQuery = "SELECT count(1) FROM prozesse WHERE ProzesseID = ?";
    private static final String processTitleCheckQuery = "SELECT count(1) FROM prozesse WHERE Titel = ?";

    @Override
    public PluginReturnValue call(TaskTicket ticket) {
        log.info("got import ticket for " + ticket.getProcessName());

        String processId = ticket.getProcessName();

        if ("true".equalsIgnoreCase(ticket.getProperties().get("deleteOldProcess"))) {
            Process process = ProcessManager.getProcessById(Integer.parseInt(processId));
            if (process != null) {
                ProcessManager.deleteProcess(process);
            }
        }

        Path processFolder = Paths.get(ticket.getProperties().get("processFolder"));
        String tempFolderName = ticket.getProperties().get("tempFolder");

        if (!Files.exists(processFolder)) {
            try {
                Files.createDirectories(processFolder);
            } catch (IOException e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }
        }

        String currentRule = ticket.getProperties().get("rule");
        boolean createNewProcessId = Boolean.valueOf(ticket.getProperties().get("createNewProcessId"));
        List<Path> folderContentList = StorageProvider.getInstance().listFiles(processFolder.toString());

        List<Path> files = new ArrayList<>();
        List<Path> folder = new ArrayList<>();
        Path importFile = null;
        for (Path path : folderContentList) {
            String filename = path.getFileName().toString();
            if (StorageProvider.getInstance().isDirectory(path)) {
                folder.add(path);
            } else if (filename.equals(processId + "_db_export.xml")) {
                importFile = path;
            } else {
                files.add(path);
            }
        }

        if (importFile == null) {
            log.error("No importable data found in " + processFolder.toString());
            return PluginReturnValue.ERROR;
        }

        Integer generatedProcessId = null;
        try {
            generatedProcessId = extractDatabaseInformationFromFile(importFile, currentRule, createNewProcessId);
        } catch (Exception e2) {
            log.error(e2);
            return PluginReturnValue.ERROR;
        }
        if (generatedProcessId == null) {
            log.error("No process id found");
            return PluginReturnValue.ERROR;
        } else {
            log.info("Stored process " + generatedProcessId);
        }

        if (StringUtils.isNotBlank(tempFolderName) && !processFolder.toString().startsWith(ConfigurationHelper.getInstance().getMetadataFolder())) {
            // copy data from temporary folder to process folder
            Path destination = Paths.get(ConfigurationHelper.getInstance().getMetadataFolder(), String.valueOf(generatedProcessId));
            if (!ConfigurationHelper.getInstance().useS3()) {
                try {
                    StorageProvider.getInstance().move(processFolder, destination);
                } catch (IOException e) {
                    log.error(e);
                }
            }
            processFolder = destination;
        }

        if (ConfigurationHelper.getInstance().useS3()) {
            // move meta.xml and meta_anchor.xml to efs
            AmazonS3 s3 = S3FileUtils.createS3Client();
            ConfigurationHelper config = ConfigurationHelper.getInstance();
            List<String> metaList = getMetaHistory(processId, s3, config);
            log.debug("downloading " + metaList.size() + " files");
            for (String key : metaList) {
                try (S3Object objMeta = s3.getObject(config.getS3Bucket(), key); InputStream is = objMeta.getObjectContent()) {
                    String basename = key.substring(key.lastIndexOf('/') + 1);
                    Path filename = processFolder.resolve(basename);
                    log.debug(filename);
                    Files.copy(is, processFolder.resolve(basename));
                    s3.deleteObject(config.getS3Bucket(), key);
                } catch (IOException e1) {
                    log.error(e1);
                }
            }
        }

        // move [id]_db_export.xml to  /import/[id]_db_export.xml
        if (StorageProvider.getInstance().isDirectory(processFolder)) {
            Path dbExportFile = Paths.get(processFolder.toString(), processId + "_db_export.xml");

            Path destinationFolder = Paths.get(processFolder.toString(), "import");
            if (!StorageProvider.getInstance().isFileExists(destinationFolder)) {
                try {
                    StorageProvider.getInstance().createDirectories(destinationFolder);
                } catch (IOException e) {
                    log.error(e);
                }
            }
            try {
                StorageProvider.getInstance().move(dbExportFile, Paths.get(destinationFolder.toString(), dbExportFile.getFileName().toString()));
            } catch (IOException e) {
                log.error(e);
            }
        }

        saveDatabaseMetadata(generatedProcessId);

        return PluginReturnValue.FINISH;
    }

    /**
     * Returns a list of all files in provided prefix matching "meta.*xml(?:.\\d)?", so meta.xml, meta_anchor.xml and their previous versions
     */
    private List<String> getMetaHistory(String processId, AmazonS3 s3, ConfigurationHelper config) {
        Pattern pattern = Pattern.compile("meta.*xml(?:.\\d)?");

        ObjectListing ol = s3.listObjects(config.getS3Bucket(), processId);
        List<String> metaList = new ArrayList<>();
        for (S3ObjectSummary os : ol.getObjectSummaries()) {
            Matcher matcher = pattern.matcher(os.getKey());
            if (matcher.find()) {
                metaList.add(os.getKey());
            }

        }
        while (ol.isTruncated()) {
            ol = s3.listNextBatchOfObjects(ol);
            for (S3ObjectSummary os : ol.getObjectSummaries()) {
                Matcher matcher = pattern.matcher(os.getKey());
                if (matcher.find()) {
                    metaList.add(os.getKey());
                }
            }
        }
        return metaList;
    }

    private Integer extractDatabaseInformationFromFile(Path importFile, String currentRule, boolean createNewProcessId) throws IOException {

        SAXBuilder builder = new SAXBuilder();
        Rule selectedRule = null;
        try {
            boolean idInUse = false;
            boolean titleInUse = false;
            Document document = builder.build(StorageProvider.getInstance().newInputStream(importFile));
            Element processElement = document.getRootElement();

            // load assigned rule TODO use selected rule from UI
            Element projectElement = processElement.getChild("project", goobiNamespace);

            Element projectTitleElement = projectElement.getChild("title", goobiNamespace);
            String projectName = projectTitleElement.getText();

            if (StringUtils.isBlank(currentRule) || currentRule.equals("Autodetect rule")) {
                for (Rule rule : ProcessImportConfiguration.getConfiguredItems()) {
                    if (rule.getRulename().equals(projectName)) {
                        selectedRule = rule;
                        break;
                    }
                }
            } else {
                for (Rule rule : ProcessImportConfiguration.getConfiguredItems()) {
                    if (rule.getRulename().equals(currentRule)) {
                        selectedRule = rule;
                        break;
                    }
                }
            }

            // no rule defined, use file
            if (selectedRule == null) {
                selectedRule = new Rule();
            }

            Process process = new Process();
            // check if id already exists, if old id is re-used
            Integer processId = null;
            if (!createNewProcessId) {
                Element idElement = processElement.getChild("id", goobiNamespace);
                // check if id is already used
                int number = getNumberOfObjectsFromDatabase(processIdCheckQuery, idElement.getText());
                if (number > 0) {
                    // id already in use
                    idInUse = true;
                }
                if (idInUse) {
                    throw new IOException("Process does already exist, abort.");
                }
                processId = Integer.parseInt(idElement.getText());
            }

            Element titleElement = processElement.getChild("title", goobiNamespace);

            process.setTitel(titleElement.getText());

            process.setIstTemplate(Boolean.valueOf(processElement.getAttributeValue("template")));

            try {
                process.setErstellungsdatum(dateConverter.parse(processElement.getChild("creationDate", goobiNamespace).getText()));
            } catch (ParseException e) {
                log.error(e);
            }
            // project
            assignProjectToProcess(processElement, process, selectedRule);

            // ruleset
            assignRulesetToProcess(processElement, process, selectedRule);

            // sorting
            Element sortingStatus = processElement.getChild("sorting", goobiNamespace);
            process.setSortHelperArticles(Integer.parseInt(sortingStatus.getAttributeValue("articles")));
            process.setSortHelperDocstructs(Integer.parseInt(sortingStatus.getAttributeValue("docstructs")));
            process.setSortHelperImages(Integer.parseInt(sortingStatus.getAttributeValue("images")));
            process.setSortHelperMetadata(Integer.parseInt(sortingStatus.getAttributeValue("metadata")));
            process.setMediaFolderExists(Boolean.parseBoolean(sortingStatus.getAttributeValue("mediaFolderExists")));
            process.setSortHelperStatus(sortingStatus.getAttributeValue("status"));

            // save process to register/get id
            saveProcess(process, processId);
            // batch
            Element batch = processElement.getChild("batch", goobiNamespace);
            if (batch != null) {
                assignBatchToProcess(process, batch);
            }

            // docket
            assignDocketToProcess(processElement, process, selectedRule);

            if (!selectedRule.getProcessRule().isSkipProcesslog()) {
                // process log
                createProcessLog(processElement.getChild("log", goobiNamespace), process);
            }
            // properties
            createProcessProperties(processElement.getChild("properties", goobiNamespace), process, selectedRule);
            // templates
            createTemplateProperties(processElement.getChild("templates", goobiNamespace), process);
            // workpiece
            createWorkpieceProperties(processElement.getChild("workpiece", goobiNamespace), process);

            // tasks
            createTasks(processElement.getChild("tasks", goobiNamespace), process, selectedRule.getProcessRule().isSkipUserImport());

            // change tasks based on a configuration
            updateTasks(process, selectedRule);

            // finally check task status

            List<Step> closedSteps = new ArrayList<>();
            List<Step> lockedSteps = new ArrayList<>();
            List<Step> inProgressSteps = new ArrayList<>();
            for (Step step : process.getSchritte()) {
                switch (step.getBearbeitungsstatusEnum()) {
                    case DONE:
                    case DEACTIVATED:
                        closedSteps.add(step);
                        break;

                    case ERROR:
                    case INWORK:
                    case OPEN:
                        inProgressSteps.add(step);
                        break;
                    case LOCKED:
                        lockedSteps.add(step);
                        break;
                }
            }
            // found no open tasks, but progress is unfinished
            if (inProgressSteps.isEmpty() && !lockedSteps.isEmpty()) {
                // open first unfinished task
                lockedSteps.get(0).setBearbeitungsstatusEnum(StepStatus.OPEN);
            }

            // TODO what if the first open step is automatic?

            // save process
            saveProcess(process, processId);

            // read metadata

            // TODO update metadata

            if (!selectedRule.getConfiguredMetadataRules().isEmpty()) {
                updateMetadata(process, selectedRule);
            }

            return process.getId();
        } catch (JDOMException | IOException e) {
            log.error(e);
            throw new IOException("File not parseable: " + e.getMessage());
        } catch (DAOException e) {
            log.error(e);
            throw new IOException("Error during saving.");
        }

    }

    @Override
    public String getTicketHandlerName() {
        return "DatabaseInformationTicket";
    }

    private void updateMetadata(Process process, Rule selectedRule) {
        // check if metadata file exists
        Path metadataFile;
        Path anchorFile;
        try {
            metadataFile = Paths.get(process.getMetadataFilePath());
            anchorFile = Paths.get(process.getMetadataFilePath().replace("meta.xml", "meta_anchor.xml"));
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
            return;
        }
        if (!StorageProvider.getInstance().isFileExists(metadataFile)) {
            return;
        }
        // read metadata file
        Document metsDocument = null;
        Document anchorDocument = null;
        SAXBuilder builder = new SAXBuilder();

        try (InputStream is = StorageProvider.getInstance().newInputStream(metadataFile)) {
            metsDocument = builder.build(is);
        } catch (IOException | JDOMException e) {
            log.error(e);
        }

        if (StorageProvider.getInstance().isFileExists(anchorFile)) {
            try (InputStream ais = StorageProvider.getInstance().newInputStream(anchorFile)) {
                anchorDocument = builder.build(ais);
            } catch (IOException | JDOMException e) {
                log.error(e);
            }
        }
        XPathFactory xFactory = XPathFactory.instance();

        for (MetadataConfigurationItem mci : selectedRule.getConfiguredMetadataRules()) {
            switch (mci.getConfigurationType()) {
                case ADD_NEW_METADATA:
                    addMetadata(metsDocument, anchorDocument, mci, xFactory);
                    break;
                case DELETE_CURRENT_METADATA:
                    deleteMetadata(metsDocument, anchorDocument, mci, xFactory);
                    break;
                case CHANGE_CURRENT_METADATA:
                    changeMetadata(metsDocument, anchorDocument, mci, xFactory);
                    break;
            }
        }

        // save metadata
        Format format = Format.getPrettyFormat();
        format.setEncoding("UTF-8");
        XMLOutputter xmlOut = new XMLOutputter(format);
        try (OutputStream os = StorageProvider.getInstance().newOutputStream(metadataFile)) {
            xmlOut.output(metsDocument, os);
        } catch (IOException e) {
            log.error(e);
        }

        if (anchorDocument != null) {
            try (OutputStream os = StorageProvider.getInstance().newOutputStream(anchorFile)) {
                xmlOut.output(anchorDocument, os);
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    private void changeMetadata(Document metsDocument, Document anchorDocument, MetadataConfigurationItem mci, XPathFactory xFactory) {
        Perl5Util perlUtil = new Perl5Util();
        String xpathFirst = "/mets:mets/mets:dmdSec[1]/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                + mci.getMetadataName() + "']";
        String xpathAll = "/mets:mets/mets:dmdSec/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                + mci.getMetadataName() + "']";
        String xpathPhysical =
                "/mets:mets/mets:dmdSec[@ID='DMDPHYS_0000']/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                        + mci.getMetadataName() + "']";
        List<Element> elementsToChange = new ArrayList<>();
        switch (mci.getPosition()) {
            case "anchor":
                if (anchorDocument != null) {
                    elementsToChange =
                            xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(anchorDocument.getRootElement());
                }
                break;
            case "top":
                elementsToChange = xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement());
                break;
            case "physical":
                elementsToChange =
                xFactory.compile(xpathPhysical, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement());
                break;
            case "all":
                elementsToChange
                .addAll(xFactory.compile(xpathAll, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement()));
                if (anchorDocument != null) {
                    List<Element> anchorElements =
                            xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(anchorDocument.getRootElement());
                    if (anchorElements != null) {
                        elementsToChange.addAll(anchorElements);
                    }
                }
                break;
        }
        for (Element element : elementsToChange) {
            if (StringUtils.isBlank(mci.getValueConditionRegex()) || perlUtil.match(mci.getValueConditionRegex(), element.getText())) {
                element.setText(perlUtil.substitute(mci.getValueReplacementRegex(), element.getText()));
            }
        }
    }

    private void deleteMetadata(Document metsDocument, Document anchorDocument, MetadataConfigurationItem mci, XPathFactory xFactory) {
        String xpathFirst = "/mets:mets/mets:dmdSec[1]/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                + mci.getMetadataName() + "']";
        String xpathAll = "/mets:mets/mets:dmdSec/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                + mci.getMetadataName() + "']";
        String xpathPhysical =
                "/mets:mets/mets:dmdSec[@ID='DMDPHYS_0000']/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi/goobi:metadata[@name='"
                        + mci.getMetadataName() + "']";
        List<Element> elementsToDelete = null;
        switch (mci.getPosition()) {
            case "anchor":
                if (anchorDocument != null) {
                    elementsToDelete =
                            xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(anchorDocument.getRootElement());
                }
                break;
            case "top":
                elementsToDelete = xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement());
                break;
            case "physical":
                elementsToDelete =
                xFactory.compile(xpathPhysical, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement());
                break;
            case "all":
                elementsToDelete = xFactory.compile(xpathAll, Filters.element(), null, goobi, mets, mods).evaluate(metsDocument.getRootElement());
                if (anchorDocument != null) {
                    elementsToDelete.addAll(
                            xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluate(anchorDocument.getRootElement()));
                }
                break;
        }
        for (Element element : elementsToDelete) {
            element.getParentElement().removeContent(element);
        }
    }

    private void addMetadata(Document metsDocument, Document anchorDocument, MetadataConfigurationItem mci, XPathFactory xFactory) {

        String xpathFirst = "/mets:mets/mets:dmdSec[1]/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi";
        String xpathPhysical = "/mets:mets/mets:dmdSec[@ID='DMDPHYS_0000']/mets:mdWrap/mets:xmlData/mods:mods/mods:extension/goobi:goobi";

        Element metadataList = null;
        if ("anchor".equals(mci.getPosition()) && anchorDocument != null) {
            metadataList = xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluateFirst(anchorDocument.getRootElement());
        } else if ("top".equals(mci.getPosition())) {
            metadataList = xFactory.compile(xpathFirst, Filters.element(), null, goobi, mets, mods).evaluateFirst(metsDocument.getRootElement());
        } else if ("physical".equals(mci.getPosition())) {
            metadataList = xFactory.compile(xpathPhysical, Filters.element(), null, goobi, mets, mods).evaluateFirst(metsDocument.getRootElement());
        }
        if (metadataList != null) {
            Element goobiMetadata = new Element("metadata", goobi);
            goobiMetadata.setAttribute("name", mci.getMetadataName());
            goobiMetadata.setText(mci.getValueReplacementRegex());
            metadataList.addContent(goobiMetadata);
        }
    }

    private void updateTasks(Process process, Rule selectedRule) {
        List<Step> stepList = process.getSchritte();
        List<Step> stepstoAdd = new ArrayList<>();
        List<Step> stepsToDelete = new ArrayList<>();
        List<StepConfigurationItem> itemList = selectedRule.getConfiguredStepRules();

        if (itemList != null) {
            for (StepConfigurationItem sci : itemList) {
                for (int i = 0; i < stepList.size(); i++) {
                    Step step = stepList.get(i);

                    if (step.getTitel().equals(sci.getCurrentStepName())) {
                        if (sci.getConfigurationType() == ConfigurationType.DELETE_CURRENT_STEP) {
                            stepsToDelete.add(step);
                        } else if (sci.getConfigurationType() == ConfigurationType.INSERT_BEFORE) {
                            Step newStep = createStep(sci, process);
                            if (newStep.getReihenfolge() == 0) {
                                newStep.setReihenfolge(step.getReihenfolge() - 1);
                            }
                            stepList.add(newStep);
                        } else if (sci.getConfigurationType() == ConfigurationType.INSERT_AFTER) {
                            Step newStep = createStep(sci, process);
                            if (newStep.getReihenfolge() == 0) {
                                newStep.setReihenfolge(step.getReihenfolge() + 1);
                            }
                            stepList.add(newStep);
                        } else {
                            changeCurrentStep(step, sci);
                        }
                        break;
                    }
                }
            }
        }
        // finally remove tasks from process list marked as to be deleted
        if (!stepsToDelete.isEmpty()) {
            for (Step stepToDelete : stepsToDelete) {
                stepList.remove(stepToDelete);
                //                for (Step step : stepList) {
                //                    if(stepToDelete.getId()==step.getId()) {
                //                        log.debug("stepToDelete Title: "+stepToDelete.getTitel()+" id: "+stepToDelete.getId());
                //                        log.debug("step Title: "+step.getTitel()+" id: "+step.getId());
                //                        stepList.remove(step);
                //                        break;
                //                    }
                //                    if (stepToDelete.getTitel().equals(step.getTitel())) {
                //                        stepList.remove(step);
                //                        break;
                //                    }
                //                }
            }
        }
    }

    private Step createStep(StepConfigurationItem sci, Process process) {
        Step step = new Step();
        step.setProzess(process);
        step.setProcessId(process.getId());
        changeCurrentStep(step, sci);
        return step;
    }

    private void changeCurrentStep(Step step, StepConfigurationItem sci) {

        if (sci.getNewStepName() != null) {
            step.setTitel(sci.getNewStepName());
        }
        if (sci.getPrioritaet() != null) {
            step.setPrioritaet(sci.getPrioritaet());
        }
        if (sci.getReihenfolge() != null) {
            step.setReihenfolge(sci.getReihenfolge());
        }

        if (sci.getHomeverzeichnisNutzen() != null) {
            step.setHomeverzeichnisNutzen(sci.getHomeverzeichnisNutzen());
        }

        if (sci.getTypMetadaten() != null) {
            step.setTypMetadaten(sci.getTypMetadaten());
        }
        if (sci.getTypAutomatisch() != null) {
            step.setTypAutomatisch(sci.getTypAutomatisch());
        }

        if (sci.getTypImagesLesen() != null) {
            step.setTypImagesLesen(sci.getTypImagesLesen());
        }
        if (sci.getTypImagesSchreiben() != null) {
            step.setTypImagesSchreiben(sci.getTypImagesSchreiben());
        }
        if (sci.getTypExportDMS() != null) {
            step.setTypExportDMS(sci.getTypExportDMS());
        }

        if (sci.getTypBeimAnnehmenAbschliessen() != null) {
            step.setTypBeimAnnehmenAbschliessen(sci.getTypBeimAnnehmenAbschliessen());
        }

        if (sci.getTypScriptStep() != null) {
            step.setTypScriptStep(sci.getTypScriptStep());
        }
        if (sci.getScriptname1() != null) {
            step.setScriptname1(sci.getScriptname1());
        }
        if (sci.getTypAutomatischScriptpfad() != null) {
            step.setTypAutomatischScriptpfad(sci.getTypAutomatischScriptpfad());
        }
        if (sci.getScriptname2() != null) {
            step.setScriptname2(sci.getScriptname2());
        }
        if (sci.getTypAutomatischScriptpfad2() != null) {
            step.setTypAutomatischScriptpfad2(sci.getTypAutomatischScriptpfad2());
        }
        if (sci.getScriptname3() != null) {
            step.setScriptname3(sci.getScriptname3());
        }
        if (sci.getTypAutomatischScriptpfad3() != null) {
            step.setTypAutomatischScriptpfad3(sci.getTypAutomatischScriptpfad3());
        }
        if (sci.getScriptname4() != null) {
            step.setScriptname4(sci.getScriptname4());
        }
        if (sci.getTypAutomatischScriptpfad4() != null) {
            step.setTypAutomatischScriptpfad4(sci.getTypAutomatischScriptpfad4());
        }
        if (sci.getScriptname5() != null) {
            step.setScriptname5(sci.getScriptname5());
        }
        if (sci.getTypAutomatischScriptpfad5() != null) {
            step.setTypAutomatischScriptpfad5(sci.getTypAutomatischScriptpfad5());
        }

        if (sci.getTypBeimAbschliessenVerifizieren() != null) {
            step.setTypBeimAbschliessenVerifizieren(sci.getTypBeimAbschliessenVerifizieren());
        }
        if (sci.getBatchStep() != null) {
            step.setBatchStep(sci.getBatchStep());
        }
        if (sci.getHttpStep() != null) {
            step.setHttpStep(sci.getHttpStep());
        }

        if (sci.getHttpUrl() != null) {
            step.setHttpUrl(sci.getHttpUrl());
        }

        if (sci.getHttpMethod() != null) {
            step.setHttpMethod(sci.getHttpMethod());
        }

        if (sci.getHttpJsonBody() != null) {
            step.setHttpJsonBody(sci.getHttpJsonBody());
        }

        if (sci.getHttpCloseStep() != null) {
            step.setHttpCloseStep(sci.getHttpCloseStep());
        }

        if (sci.getHttpEscapeBodyJson() != null) {
            step.setHttpEscapeBodyJson(sci.getHttpEscapeBodyJson());
        }

        if (sci.getBenutzergruppen() != null) {
            step.setBenutzergruppen(sci.getBenutzergruppen());
        }

        if (sci.getStepPlugin() != null) {
            step.setStepPlugin(sci.getStepPlugin());
        }

        if (sci.getValidationPlugin() != null) {
            step.setValidationPlugin(sci.getValidationPlugin());
        }

        if (sci.getDelayStep() != null) {
            step.setDelayStep(sci.getDelayStep());
        }
        if (sci.getUpdateMetadataIndex() != null) {
            step.setUpdateMetadataIndex(sci.getUpdateMetadataIndex());
        }

        if (sci.getGenerateDocket() != null) {
            step.setGenerateDocket(sci.getGenerateDocket());
        }

        if (sci.getStepStatus() != null) {
            step.setBearbeitungsstatusEnum(StepStatus.getStatusFromValue(sci.getStepStatus()));
        }
    }

    private void createWorkpieceProperties(Element properties, Process process) {
        if (properties != null && properties.getChildren() != null) {
            Masterpiece masterpiece = new Masterpiece();
            List<Masterpiece> list = new ArrayList<>();
            list.add(masterpiece);
            masterpiece.setProzess(process);
            masterpiece.setProcessId(process.getId());
            process.setWerkstuecke(list);

            for (Element propertyElement : properties.getChildren()) {
                Masterpieceproperty property = new Masterpieceproperty();
                property.setContainer(Integer.parseInt(propertyElement.getAttributeValue("container")));
                Element creationDate = propertyElement.getChild("creationDate", goobiNamespace);
                if (creationDate != null && StringUtils.isNotBlank(creationDate.getText())) {
                    try {
                        property.setCreationDate(dateConverter.parse(propertyElement.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                property.setType(PropertyType.String);
                property.setTitel(propertyElement.getChild("name", goobiNamespace).getText());
                property.setWert(propertyElement.getChild("value", goobiNamespace).getText());
                masterpiece.getEigenschaftenList().add(property);
            }
        }
    }

    private void createTemplateProperties(Element properties, Process process) {
        if (properties != null && properties.getChildren() != null) {
            Template template = new Template();
            List<Template> list = new ArrayList<>();
            list.add(template);
            template.setProzess(process);
            template.setProcessId(process.getId());
            process.setVorlagen(list);
            for (Element propertyElement : properties.getChildren()) {
                Templateproperty property = new Templateproperty();
                property.setContainer(Integer.parseInt(propertyElement.getAttributeValue("container")));
                Element creationDate = propertyElement.getChild("creationDate", goobiNamespace);
                if (creationDate != null && StringUtils.isNotBlank(creationDate.getText())) {
                    try {
                        property.setCreationDate(dateConverter.parse(propertyElement.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                property.setType(PropertyType.String);
                property.setTitel(propertyElement.getChild("name", goobiNamespace).getText());
                property.setWert(propertyElement.getChild("value", goobiNamespace).getText());
                template.getEigenschaftenList().add(property);
            }
        }

    }

    private void createProcessProperties(Element properties, Process process, Rule selectedRule) {
        if (properties != null && properties.getChildren() != null) {
            for (Element propertyElement : properties.getChildren()) {

                String propertyName = propertyElement.getChild("name", goobiNamespace).getText();
                String propertyValue = propertyElement.getChild("value", goobiNamespace).getText();

                // update properties with configured rules
                if (!selectedRule.getConfiguredPropertyRules().isEmpty()) {
                    for (PropertyConfigurationItem pci : selectedRule.getConfiguredPropertyRules()) {
                        if ((StringUtils.isBlank(pci.getOldPropertyName()) || pci.getOldPropertyName().equalsIgnoreCase(propertyName))
                                && StringUtils.isBlank(pci.getOldPropertyValue()) || propertyValue.matches(pci.getOldPropertyValue())) {

                            if (StringUtils.isNotBlank(pci.getNewPropertyName())) {
                                propertyName = pci.getNewPropertyName();
                            }
                            if (StringUtils.isNotBlank(pci.getNewPropertyValue())) {
                                propertyValue = pci.getNewPropertyValue();
                            }
                        }
                    }

                }

                Processproperty property = new Processproperty();

                property.setContainer(Integer.parseInt(propertyElement.getAttributeValue("container")));
                Element creationDate = propertyElement.getChild("creationDate", goobiNamespace);
                if (creationDate != null && StringUtils.isNotBlank(creationDate.getText())) {
                    try {
                        property.setCreationDate(dateConverter.parse(creationDate.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                property.setType(PropertyType.String);
                property.setProzess(process);
                property.setTitel(propertyName);
                property.setWert(propertyValue);
                process.getEigenschaftenList().add(property);
            }
        }
    }

    private void createTasks(Element tasks, Process process, boolean skipUserImport) {
        if (tasks != null && tasks.getChildren() != null) {
            for (Element taskElement : tasks.getChildren()) {
                Step step = new Step();
                step.setProzess(process);
                step.setProcessId(process.getId());
                process.getSchritteList().add(step);
                step.setTitel(taskElement.getChild("name", goobiNamespace).getText());
                step.setPrioritaet(Integer.parseInt(taskElement.getChild("priority", goobiNamespace).getText()));
                step.setReihenfolge(Integer.parseInt(taskElement.getChild("order", goobiNamespace).getText()));
                step.setBearbeitungsstatusEnum(
                        StepStatus.getStatusFromValue(Integer.parseInt(taskElement.getChild("status", goobiNamespace).getText())));
                Element processingTime = taskElement.getChild("processingTime", goobiNamespace);
                if (processingTime != null && StringUtils.isNotBlank(processingTime.getText())) {
                    try {
                        step.setBearbeitungszeitpunkt(dateConverter.parse(processingTime.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                Element processingStartTime = taskElement.getChild("processingStartTime", goobiNamespace);
                if (processingStartTime != null && StringUtils.isNotBlank(processingStartTime.getText())) {
                    try {
                        step.setBearbeitungsbeginn(dateConverter.parse(processingStartTime.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                Element processingEndTime = taskElement.getChild("processingEndTime", goobiNamespace);
                if (processingEndTime != null && StringUtils.isNotBlank(processingEndTime.getText())) {
                    try {
                        step.setBearbeitungsende(dateConverter.parse(processingEndTime.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                if (!skipUserImport) {
                    Element userElement = taskElement.getChild("user", goobiNamespace);
                    if (userElement != null && StringUtils.isNotBlank(userElement.getAttributeValue("login"))) {
                        User user = UserManager.getUserByLogin(userElement.getAttributeValue("login"));
                        if (user == null) {
                            user = new User();
                            user.setLogin(userElement.getAttributeValue("login"));
                            String userName = userElement.getText();
                            if (StringUtils.isNotBlank(userName)) {
                                if (userName.contains(",")) {
                                    user.setNachname(userName.substring(0, userName.indexOf(",")));
                                    user.setVorname(userName.substring(userName.indexOf(",") + 1));
                                }
                            }
                            user.setIstAktiv(false);
                            user.setIsVisible("deleted");
                            Element institutionElement = userElement.getChild("institution", goobiNamespace);
                            if (institutionElement == null) {
                                Institution inst = InstitutionManager.getAllInstitutionsAsList().get(0);
                                user.setInstitution(inst);
                            } else {
                                Institution institution = getInstitution(institutionElement);
                                user.setInstitution(institution);
                            }
                            try {
                                UserManager.saveUser(user);
                            } catch (DAOException e) {
                                log.error(e);
                            }

                            step.setBearbeitungsbenutzer(user);
                        }
                    }
                }
                step.setEditTypeEnum(StepEditType.getTypeFromValue(Integer.parseInt(taskElement.getChild("editionType", goobiNamespace).getText())));
                Element configuration = taskElement.getChild("configuration", goobiNamespace);
                step.setHomeverzeichnisNutzen(Short.parseShort(configuration.getAttributeValue("useHomeDirectory")));
                step.setTypMetadaten(Boolean.parseBoolean(configuration.getAttributeValue("useMetsEditor")));
                step.setTypAutomatisch(Boolean.parseBoolean(configuration.getAttributeValue("isAutomatic")));
                step.setTypImagesLesen(Boolean.parseBoolean(configuration.getAttributeValue("readImages")));
                step.setTypImagesSchreiben(Boolean.parseBoolean(configuration.getAttributeValue("writeImages")));
                step.setTypExportDMS(Boolean.parseBoolean(configuration.getAttributeValue("export")));
                step.setTypBeimAbschliessenVerifizieren(Boolean.parseBoolean(configuration.getAttributeValue("verifyOnFinalize")));
                step.setTypBeimAnnehmenAbschliessen(Boolean.parseBoolean(configuration.getAttributeValue("finalizeOnAccept")));
                step.setDelayStep(Boolean.parseBoolean(configuration.getAttributeValue("delayStep")));
                step.setUpdateMetadataIndex(Boolean.parseBoolean(configuration.getAttributeValue("updateMetadataIndex")));
                step.setGenerateDocket(Boolean.parseBoolean(configuration.getAttributeValue("generateDocket")));
                step.setBatchStep(Boolean.parseBoolean(configuration.getAttributeValue("batchStep")));
                step.setStepPlugin(configuration.getAttributeValue("stepPlugin"));
                step.setValidationPlugin(configuration.getAttributeValue("validationPlugin"));

                Element scriptStep = taskElement.getChild("scriptStep", goobiNamespace);
                if (scriptStep.getAttributeValue("scriptStep").equals("true")) {
                    step.setTypScriptStep(true);
                    step.setScriptname1(scriptStep.getAttributeValue("scriptName1"));
                    step.setTypAutomatischScriptpfad(scriptStep.getAttributeValue("scriptPath1"));

                    step.setScriptname2(scriptStep.getAttributeValue("scriptName2"));
                    step.setTypAutomatischScriptpfad2(scriptStep.getAttributeValue("scriptPath2"));

                    step.setScriptname3(scriptStep.getAttributeValue("scriptName3"));
                    step.setTypAutomatischScriptpfad3(scriptStep.getAttributeValue("scriptPath3"));

                    step.setScriptname4(scriptStep.getAttributeValue("scriptName4"));
                    step.setTypAutomatischScriptpfad4(scriptStep.getAttributeValue("scriptPath4"));

                    step.setScriptname5(scriptStep.getAttributeValue("scriptName5"));
                    step.setTypAutomatischScriptpfad5(scriptStep.getAttributeValue("scriptPath4"));
                }
                Element httpStep = taskElement.getChild("httpStep", goobiNamespace);
                if (httpStep.getAttributeValue("httpStep").equals("true")) {
                    step.setHttpStep(true);
                    step.setHttpCloseStep(Boolean.parseBoolean(httpStep.getAttributeValue("httpCloseStep")));
                    step.setHttpJsonBody(httpStep.getAttributeValue("httpJsonBody"));
                    step.setHttpMethod(httpStep.getAttributeValue("httpMethod"));
                    step.setHttpUrl(httpStep.getAttributeValue("httpUrl"));
                    step.setHttpEscapeBodyJson(Boolean.parseBoolean(httpStep.getAttributeValue("httpEscapeBodyJson")));
                }
                //                assignedUserGroups
                Element assignedUserGroups = taskElement.getChild("assignedUserGroups", goobiNamespace);
                if (assignedUserGroups != null && assignedUserGroups.getChildren() != null) {
                    for (Element groupElement : assignedUserGroups.getChildren()) {
                        Usergroup ug = UsergroupManager.getUsergroupByName(groupElement.getAttributeValue("name"));
                        if (ug == null) {
                            ug = createNewGroup(groupElement);
                        }
                        step.getBenutzergruppen().add(ug);

                    }
                }
            }
        }
    }

    private Institution getInstitution(Element institutionElement) {
        String institutionName = institutionElement.getAttributeValue("longName");
        Institution institution = null;
        List<Institution> existingInstitutions = InstitutionManager.getAllInstitutionsAsList();
        for (Institution other : existingInstitutions) {
            if (other.getLongName().equals(institutionName)) {
                institution = other;
            }
        }
        if (institution == null) {
            institution = new Institution();
            institution.setLongName(institutionElement.getAttributeValue("longName"));
            institution.setShortName(institutionElement.getAttributeValue("shortName"));

            institution.setAllowAllAuthentications(Boolean.valueOf(institutionElement.getAttributeValue("allowAllAuthentications")));
            institution.setAllowAllDockets(Boolean.valueOf(institutionElement.getAttributeValue("allowAllDockets")));
            institution.setAllowAllPlugins(Boolean.valueOf(institutionElement.getAttributeValue("allowAllPlugins")));
            institution.setAllowAllRulesets(Boolean.valueOf(institutionElement.getAttributeValue("allowAllRulesets")));
            InstitutionManager.saveInstitution(institution);
        }
        return institution;
    }

    private Usergroup createNewGroup(Element groupElement) {
        Usergroup ug = new Usergroup();
        ug.setBerechtigung(Integer.parseInt(groupElement.getAttributeValue("accessLevel")));
        ug.setTitel(groupElement.getAttributeValue("name"));
        List<String> assignedRoles = new ArrayList<>();
        for (Element role : groupElement.getChildren("role", goobiNamespace)) {
            assignedRoles.add(role.getText());
        }
        ug.setUserRoles(assignedRoles);

        Element institutionElement = groupElement.getChild("institution", goobiNamespace);
        if (institutionElement == null) {
            Institution inst = InstitutionManager.getAllInstitutionsAsList().get(0);
            ug.setInstitution(inst);
        } else {
            Institution institution = getInstitution(institutionElement);
            ug.setInstitution(institution);
        }

        try {
            UsergroupManager.saveUsergroup(ug);
        } catch (DAOException e) {
            log.error(e);
        }

        return ug;
    }

    private void createProcessLog(Element logElement, Process process) {
        if (logElement != null && logElement.getChildren() != null) {
            for (Element entryElement : logElement.getChildren()) {
                LogEntry entry = new LogEntry();
                entry.setContent(entryElement.getChild("content", goobiNamespace).getText());
                entry.setProcessId(process.getId());
                Element creationDateElement = entryElement.getChild("creationDate", goobiNamespace);
                if (creationDateElement != null && StringUtils.isNotBlank(creationDateElement.getText())) {
                    try {
                        entry.setCreationDate(dateConverter.parse(creationDateElement.getText()));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                entry.setType(LogType.getByTitle(entryElement.getChild("type", goobiNamespace).getText()));
                if (entryElement.getChild("secondContent", goobiNamespace) != null) {
                    entry.setSecondContent(entryElement.getChild("secondContent", goobiNamespace).getText());
                }
                if (entryElement.getChild("thirdContent", goobiNamespace) != null) {
                    entry.setThirdContent(entryElement.getChild("thirdContent", goobiNamespace).getText());
                }
                entry.setUserName(entryElement.getChild("user", goobiNamespace).getText());
                process.getProcessLog().add(entry);
            }

        }
    }

    private void assignProjectToProcess(Element processElement, Process process, Rule selectedRule) {
        Element projectElement = processElement.getChild("project", goobiNamespace);

        Element projectTitleElement = projectElement.getChild("title", goobiNamespace);
        Project project = null;
        String projectName = projectTitleElement.getText();
        // check if project should be overwritten
        if (!selectedRule.getConfiguredProjectRules().isEmpty()) {
            for (ProjectConfigurationItem pci : selectedRule.getConfiguredProjectRules()) {
                // replace project name, if old project name field is empty or matches the project name in db file
                if (StringUtils.isBlank(pci.getOldProjectName()) || pci.getOldProjectName().equalsIgnoreCase(projectName)) {
                    projectName = pci.getNewProjectName();
                    break;
                }
            }
        }

        try {
            project = ProjectManager.getProjectByName(projectName);
        } catch (DAOException e) {
            log.error(e);
        }
        if (project == null) {
            project = new Project();
            project.setTitel(projectName);
            project.setProjectIsArchived(Boolean.parseBoolean(projectElement.getAttributeValue("archived")));

            project.setFileFormatInternal(projectElement.getChild("fileFormatInternal", goobiNamespace).getText());
            project.setFileFormatDmsExport(projectElement.getChild("fileFormatDmsExport", goobiNamespace).getText());

            Element startDateElement = projectElement.getChild("startDate", goobiNamespace);
            Element endDateElement = projectElement.getChild("endDate", goobiNamespace);
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
            Element pages = projectElement.getChild("pages", goobiNamespace);
            Element volumes = projectElement.getChild("volumes", goobiNamespace);
            if (StringUtils.isNotBlank(pages.getText())) {
                project.setNumberOfPages(Integer.parseInt(pages.getText()));
            }
            if (StringUtils.isNotBlank(volumes.getText())) {
                project.setNumberOfVolumes(Integer.parseInt(volumes.getText()));
            }

            // export configuration
            Element exportConfiguration = projectElement.getChild("exportConfiguration", goobiNamespace);
            project.setUseDmsImport(Boolean.parseBoolean(exportConfiguration.getAttributeValue("useDmsImport")));
            project.setDmsImportCreateProcessFolder(Boolean.parseBoolean(exportConfiguration.getAttributeValue("dmsImportCreateProcessFolder")));
            Element dmsImportTimeOut = exportConfiguration.getChild("dmsImportTimeOut", goobiNamespace);
            if (StringUtils.isNotBlank(dmsImportTimeOut.getText())) {
                project.setDmsImportTimeOut(Integer.parseInt(dmsImportTimeOut.getText()));
            }
            project.setDmsImportRootPath(getTextFromElement(exportConfiguration.getChild("dmsImportRootPath", goobiNamespace)));
            project.setDmsImportImagesPath(getTextFromElement(exportConfiguration.getChild("dmsImportImagesPath", goobiNamespace)));
            project.setDmsImportImagesPath(getTextFromElement(exportConfiguration.getChild("dmsImportImagesPath", goobiNamespace)));
            project.setDmsImportSuccessPath(getTextFromElement(exportConfiguration.getChild("dmsImportSuccessPath", goobiNamespace)));
            project.setDmsImportErrorPath(getTextFromElement(exportConfiguration.getChild("dmsImportErrorPath", goobiNamespace)));

            // mets configuration
            Element metsConfiguration = projectElement.getChild("metsConfiguration", goobiNamespace);
            project.setMetsRightsOwnerLogo(getTextFromElement(metsConfiguration.getChild("metsRightsOwnerLogo", goobiNamespace)));
            project.setMetsRightsOwnerSite(getTextFromElement(metsConfiguration.getChild("metsRightsOwnerSite", goobiNamespace)));
            project.setMetsRightsOwnerMail(getTextFromElement(metsConfiguration.getChild("metsRightsOwnerMail", goobiNamespace)));
            project.setMetsDigiprovReference(getTextFromElement(metsConfiguration.getChild("metsDigiprovReference", goobiNamespace)));
            project.setMetsDigiprovPresentation(getTextFromElement(metsConfiguration.getChild("metsDigiprovPresentation", goobiNamespace)));
            project.setMetsDigiprovReferenceAnchor(getTextFromElement(metsConfiguration.getChild("metsDigiprovReferenceAnchor", goobiNamespace)));
            project.setMetsPointerPath(getTextFromElement(metsConfiguration.getChild("metsPointerPath", goobiNamespace)));
            project.setMetsPointerPathAnchor(getTextFromElement(metsConfiguration.getChild("metsPointerPathAnchor", goobiNamespace)));
            project.setMetsPurl(getTextFromElement(metsConfiguration.getChild("metsPurl", goobiNamespace)));
            project.setMetsContentIDs(getTextFromElement(metsConfiguration.getChild("metsContentIDs", goobiNamespace)));
            project.setMetsRightsSponsor(getTextFromElement(metsConfiguration.getChild("metsRightsSponsor", goobiNamespace)));
            project.setMetsRightsSponsorLogo(getTextFromElement(metsConfiguration.getChild("metsRightsSponsorLogo", goobiNamespace)));
            project.setMetsRightsSponsorSiteURL(getTextFromElement(metsConfiguration.getChild("metsRightsSponsorSiteURL", goobiNamespace)));
            project.setMetsRightsLicense(getTextFromElement(metsConfiguration.getChild("metsRightsLicense", goobiNamespace)));

            Element institutionElement = projectElement.getChild("institution", goobiNamespace);
            if (institutionElement == null) {
                Institution inst = InstitutionManager.getAllInstitutionsAsList().get(0);
                project.setInstitution(inst);
            } else {
                Institution institution = getInstitution(institutionElement);
                project.setInstitution(institution);
            }
            //       TODO     Element institutionElement = projectElement.getChild("institution", goobiNamespace);
            //            InstitutionManager.getInstitutionById(arg0)
            //            asd
            // filegroups
            Element fileGroups = projectElement.getChild("fileGroups", goobiNamespace);
            if (fileGroups != null) {
                for (Element grpElement : fileGroups.getChildren()) {
                    ProjectFileGroup pfg = new ProjectFileGroup();
                    pfg.setFolder(grpElement.getAttributeValue("folder"));
                    pfg.setMimetype(grpElement.getAttributeValue("mimetype"));
                    pfg.setName(grpElement.getAttributeValue("name"));
                    pfg.setPath(grpElement.getAttributeValue("path"));
                    pfg.setProject(project);
                    pfg.setSuffix(grpElement.getAttributeValue("suffix"));
                    project.getFilegroups().add(pfg);
                }
            }
            try {
                ProjectManager.saveProject(project);
            } catch (DAOException e) {
                log.error(e);
            }
        }

        process.setProjekt(project);
    }

    private void assignBatchToProcess(Process process, Element batch) {

        String batchId = batch.getAttributeValue("id");
        if (StringUtils.isNotBlank(batchId) && !batchId.equals("0")) {
            // check for existing batch
            Batch b = ProcessManager.getBatchById(Integer.parseInt(batchId));
            // or create new batch
            if (b == null) {
                b = new Batch();
                b.setBatchId(Integer.parseInt(batchId));
                b.setBatchLabel(batch.getAttributeValue("label"));
                b.setBatchName(batch.getAttributeValue("name"));
                String batchStartDate = batch.getAttributeValue("startDate");
                if (StringUtils.isNotBlank(batchStartDate)) {
                    try {
                        b.setStartDate(dateConverter.parse(batchStartDate));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
                String batchEndDate = batch.getAttributeValue("endDate");
                if (StringUtils.isNotBlank(batchEndDate)) {
                    try {
                        b.setEndDate(dateConverter.parse(batchEndDate));
                    } catch (ParseException e) {
                        log.error(e);
                    }
                }
            }
            process.setBatch(b);
        }
    }

    private void assignRulesetToProcess(Element processElement, Process process, Rule selectedRule) {
        Element rulesetElement = processElement.getChild("ruleset", goobiNamespace);

        String rulesetName = rulesetElement.getAttributeValue("name");
        String filename = rulesetElement.getAttributeValue("filename");
        // check if ruleset should be overwritten
        if (!selectedRule.getConfiguredRulesetRules().isEmpty()) {
            for (RulesetConfigurationItem rci : selectedRule.getConfiguredRulesetRules()) {
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

        // check if ruleset exist, otherwise create it
        Ruleset ruleset = null;
        try {
            ruleset = RulesetManager.getRulesetByName(rulesetName);
        } catch (DAOException e) {
            log.error(e);
        }
        if (ruleset == null) {
            ruleset = new Ruleset();
            ruleset.setTitel(rulesetName);
            ruleset.setDatei(filename);
            try {
                RulesetManager.saveRuleset(ruleset);
            } catch (DAOException e) {
                log.error(e);
            }
        }
        process.setRegelsatz(ruleset);
    }

    private void assignDocketToProcess(Element processElement, Process process, Rule selectedRule) {
        Element docketElement = processElement.getChild("docket", goobiNamespace);
        String docketName = "Default docket";
        String docketFile = "docket.xsl";

        Docket docket = null;
        if (docketElement != null) {
            docketName = docketElement.getAttributeValue("name");
            docketFile = docketElement.getAttributeValue("file");
        }
        //  check if docket should be overwritten
        if (!selectedRule.getConfiguredDocketRules().isEmpty()) {
            for (DocketConfigurationItem dci : selectedRule.getConfiguredDocketRules()) {
                // replace docket, if old name field is empty or matches the name in db file
                if (StringUtils.isBlank(dci.getOldDocketName()) || dci.getOldDocketName().equalsIgnoreCase(docketName)) {
                    if (StringUtils.isNotBlank(dci.getNewDocketName())) {
                        docketName = dci.getNewDocketName();
                    }
                    if (StringUtils.isNotBlank(dci.getNewFileName())) {
                        docketFile = dci.getNewFileName();
                    }
                    break;
                }
            }
        }

        try {
            docket = DocketManager.getDocketByName(docketName);
        } catch (DAOException e) {
            log.error(e);
        }

        if (docket == null) {
            docket = new Docket();
            docket.setName(docketName);
            docket.setFile(docketFile);
            try {
                DocketManager.saveDocket(docket);
            } catch (DAOException e) {
                log.error(e);
            }
        }
        process.setDocket(docket);
    }

    private int getNumberOfObjectsFromDatabase(String query, String parameter) {
        Connection connection = null;
        try {
            connection = MySQLHelper.getInstance().getConnection();
            return new QueryRunner().query(connection, query, MySQLHelper.resultSetToIntegerHandler, parameter);
        } catch (SQLException e) {
            log.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                }
            }
        }
        return 0;
    }

    public static void saveProcess(Process o, Integer id) throws DAOException {
        if (o.getId() == null) {
            insertProcess(o, id);
        } else {
            ProcessManager.saveProcessInformation(o);
        }
        if (o.getBatch() != null) {
            ProcessManager.saveBatch(o.getBatch());
        }
        List<Step> stepList = o.getSchritte();
        for (Step s : stepList) {
            StepManager.saveStep(s);
        }

        List<Processproperty> properties = o.getEigenschaften();
        for (Processproperty pe : properties) {
            PropertyManager.saveProcessProperty(pe);
        }

        for (Masterpiece object : o.getWerkstuecke()) {
            MasterpieceManager.saveMasterpiece(object);
        }

        List<Template> templates = o.getVorlagen();
        for (Template template : templates) {
            TemplateManager.saveTemplate(template);
        }

        for (LogEntry logEntry : o.getProcessLog()) {
            ProcessManager.saveLogEntry(logEntry);
        }

    }

    private static void insertProcess(Process o, Integer id) {
        StringBuilder insertQuery = new StringBuilder();

        insertQuery.append("INSERT INTO prozesse (");
        if (id != null) {
            insertQuery.append("ProzesseID, ");
        }

        insertQuery.append("Titel, ausgabename, IstTemplate, swappedOut, inAuswahllisteAnzeigen, sortHelperStatus, ");
        insertQuery.append("sortHelperImages, sortHelperArticles, erstellungsdatum, ProjekteID, MetadatenKonfigurationID, sortHelperDocstructs, ");
        insertQuery.append("sortHelperMetadata, batchID, docketID, mediaFolderExists");
        insertQuery.append(") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?");
        if (id != null) {
            insertQuery.append(", ?");
        }
        insertQuery.append(")");

        Connection connection = null;
        try {
            connection = MySQLHelper.getInstance().getConnection();
            QueryRunner run = new QueryRunner();
            if (id != null) {
                id = run.insert(connection, insertQuery.toString(), MySQLHelper.resultSetToIntegerHandler, id, o.getTitel(), o.getAusgabename(),
                        o.isIstTemplate(), o.isSwappedOutHibernate(), o.isInAuswahllisteAnzeigen(), o.getSortHelperStatus(), o.getSortHelperImages(),
                        o.getSortHelperArticles(), new Timestamp(o.getErstellungsdatum().getTime()), o.getProjekt().getId(), o.getRegelsatz().getId(),
                        o.getSortHelperDocstructs(), o.getSortHelperMetadata(), o.getBatch() == null ? null : o.getBatch().getBatchId(),
                                o.getDocket() == null ? null : o.getDocket().getId(), o.isMediaFolderExists());
            } else {
                id = run.insert(connection, insertQuery.toString(), MySQLHelper.resultSetToIntegerHandler, o.getTitel(), o.getAusgabename(),
                        o.isIstTemplate(), o.isSwappedOutHibernate(), o.isInAuswahllisteAnzeigen(), o.getSortHelperStatus(), o.getSortHelperImages(),
                        o.getSortHelperArticles(), new Timestamp(o.getErstellungsdatum().getTime()), o.getProjekt().getId(), o.getRegelsatz().getId(),
                        o.getSortHelperDocstructs(), o.getSortHelperMetadata(), o.getBatch() == null ? null : o.getBatch().getBatchId(),
                                o.getDocket() == null ? null : o.getDocket().getId(), o.isMediaFolderExists());
            }
            o.setId(id);
        } catch (SQLException e) {
            log.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
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

    private void saveDatabaseMetadata(Integer processid) {
        String metdatdaPath = Paths.get(ConfigurationHelper.getInstance().getMetadataFolder(), "" + processid, "meta.xml").toString();

        String anchorPath = metdatdaPath.replace("meta.xml", "meta_anchor.xml");
        Path metadataFile = Paths.get(metdatdaPath);
        Path anchorFile = Paths.get(anchorPath);
        Map<String, List<String>> pairs = new HashMap<>();

        HelperSchritte.extractMetadata(metadataFile, pairs);

        if (StorageProvider.getInstance().isFileExists(anchorFile)) {
            HelperSchritte.extractMetadata(anchorFile, pairs);
        }

        MetadataManager.updateMetadata(processid, pairs);

        // now add all authority fields to the metadata pairs
        HelperSchritte.extractAuthorityMetadata(metadataFile, pairs);
        if (StorageProvider.getInstance().isFileExists(anchorFile)) {
            HelperSchritte.extractAuthorityMetadata(anchorFile, pairs);
        }
        MetadataManager.updateJSONMetadata(processid, pairs);

    }
}
