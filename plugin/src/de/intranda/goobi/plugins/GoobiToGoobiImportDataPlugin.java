package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;

import org.apache.commons.lang.StringUtils;
import org.goobi.api.mq.QueueType;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketGenerator;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import de.intranda.goobi.importrules.ProcessImportConfiguration;
import de.intranda.goobi.importrules.Rule;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.S3FileUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class GoobiToGoobiImportDataPlugin implements IAdministrationPlugin {

    @Getter
    private String title = "intranda_administration_goobi2goobi_import_data";

    @Getter
    @Setter
    private String currentRule;
    private List<String> allRulenames = new ArrayList<>();

    @Setter
    private List<String> allFilenames = new ArrayList<>();
    private Future<List<String>> futureFilenames;

    @Getter
    @Setter
    private List<String> selectedFilenames = new ArrayList<>();

    public List<Rule> getConfigurationItemList() {
        return ProcessImportConfiguration.getConfiguredItems();
    }

    public void importSelectedFiles() {
        if (selectedFilenames == null || selectedFilenames.isEmpty()) {
            Helper.setFehlerMeldung("plugin_administration_dataimport_selection_error");
            return;
        }

        if ("Select all".equals(selectedFilenames.get(0))) {
            selectedFilenames = allFilenames.subList(1, allFilenames.size());
        }
        String folder = ProcessImportConfiguration.getTemporaryFolderToImport();
        if (StringUtils.isBlank(folder)) {
            folder = ProcessImportConfiguration.getImportPath();
        }
        for (String processId : selectedFilenames) {
            // create ticket

            TaskTicket importTicket = TicketGenerator.generateSimpleTicket("DatabaseInformationTicket");

            //filename of xml file is "<processId>_db_export.xml"
            int slashIdx = processId.indexOf('/');
            if (slashIdx >= 0) {
                processId = processId.substring(0, slashIdx);
            }
            importTicket.setProcessName(processId);

            Path processFolder = Paths.get(folder, processId);

            importTicket.getProperties().put("processFolder", processFolder.toString());
            importTicket.getProperties().put("createNewProcessId", String.valueOf(ProcessImportConfiguration.isCreateNewProcessId()));
            importTicket.getProperties().put("tempFolder", ProcessImportConfiguration.getTemporaryFolderToImport());
            if (StringUtils.isBlank(currentRule)) {
                importTicket.getProperties().put("rule", "Autodetect rule");
            } else {
                importTicket.getProperties().put("rule", currentRule);
            }
            try {
                TicketGenerator.submitInternalTicket(importTicket, QueueType.SLOW_QUEUE, "goobi2goobiImport", 0);
            } catch (JMSException e) {
            }
        }

        Helper.setMeldung(Helper.getTranslation("plugin_administration_dataimport_success", "" + selectedFilenames.size()));
    }

    public List<String> getAllFilenames() {
        if (allFilenames.isEmpty() && futureFilenames != null) {
            try {
                allFilenames = futureFilenames.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Helper.setFehlerMeldung("Data collection is not complete, please reload the page in a few seconds");
            }
            if (!allFilenames.isEmpty()) {
                allFilenames.add(0, "Select all");
            }
        }

        return allFilenames;
    }

    public void generateAllFilenames() {
        allFilenames = new ArrayList<>();

        Callable<List<String>> callable = () -> {
            List<String> allFilenames = new ArrayList<>();
            if (ConfigurationHelper.getInstance().useS3()) {
                //
                AmazonS3 s3 = S3FileUtils.createS3Client();
                String bucket = ProcessImportConfiguration.getBucket();
                String dbExportPrefix = ProcessImportConfiguration.getDbExportPrefix();
                ListObjectsRequest req = new ListObjectsRequest().withBucketName(bucket).withPrefix(dbExportPrefix);
                ObjectListing listing = s3.listObjects(req);
                for (S3ObjectSummary os : listing.getObjectSummaries()) {
                    String key = os.getKey();
                    String newKey = key.substring(dbExportPrefix.length());
                    allFilenames.add(newKey);
                }
                while (listing.isTruncated()) {
                    listing = s3.listNextBatchOfObjects(listing);
                    for (S3ObjectSummary os : listing.getObjectSummaries()) {
                        String key = os.getKey();
                        String newKey = key.substring(dbExportPrefix.length());
                        allFilenames.add(newKey);
                    }
                }
            } else {
                // search for all  folder names in /opt/digiverso/goobi/metadata/
                // check, if a file *_db_export.xml exists in the process folder
                // return the folder names
                try {
                    String folder = ProcessImportConfiguration.getTemporaryFolderToImport();
                    if (StringUtils.isBlank(folder)) {
                        folder = ProcessImportConfiguration.getImportPath();
                    }
                    Files.find(Paths.get(folder), 2,
                            (p, file) -> file.isRegularFile() && p.getFileName().toString().matches(".*_db_export.xml"))
                            .forEach(p -> allFilenames.add(p.getParent().getFileName().toString()));
                } catch (IOException e) {
                    log.error(e);
                }
            }
            return allFilenames;
        };
        ExecutorService service = Executors.newSingleThreadExecutor();
        futureFilenames = service.submit(callable);
        service.shutdown();

    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_goobi2goobi_import.xhtml";
    }

    public List<String> getAllRuleNames() {
        if (allRulenames.isEmpty()) {
            allRulenames.add("Autodetect rule");
            for (Rule rule : ProcessImportConfiguration.getConfiguredItems()) {
                allRulenames.add(rule.getRulename());
            }

        }

        return allRulenames;
    }

    public void reloadRules() {
        ProcessImportConfiguration.resetRules();
        allRulenames.clear();
    }
}
