package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;

import org.apache.commons.lang.StringUtils;
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
public class ImportProcessDatebasePlugin implements IAdministrationPlugin {

    @Getter
    private String title = "goobi-plugin-administration-database-information";

    @Getter
    @Setter
    private String currentRule;
    private List<String> allRulenames = new ArrayList<>();

    @Getter
    @Setter
    private List<String> allFilenames = new ArrayList<>();

    @Getter
    @Setter
    private List<String> selectedFilenames = new ArrayList<>();

    public List<Rule> getConfigurationItemList() {
        return ProcessImportConfiguration.getConfiguredItems();
    }

    public void importSelectedFiles() {
        if (selectedFilenames == null || selectedFilenames.isEmpty()) {
            Helper.setFehlerMeldung("TODO");
            return;
        }

        if ("Select all".equals(selectedFilenames.get(0))) {
            selectedFilenames = allFilenames;
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

            Path processFolder = Paths.get(ProcessImportConfiguration.getImportPath(), processId);

            importTicket.getProperties().put("processFolder", processFolder.toString());
            if (StringUtils.isBlank(currentRule)) {
                importTicket.getProperties().put("rule", "Autodetect rule");
            } else {
                importTicket.getProperties().put("rule", currentRule);
            }
            try {
                TicketGenerator.submitTicket(importTicket, false);
            } catch (JMSException e) {
            }
        }

        Helper.setMeldung(Helper.getTranslation("plugin_adiministration_dataimport_success", "" + selectedFilenames.size()));
    }

    public void generateAllFilenames() {
        // TODO find equivalent for S3
        // search for all  folder names in /opt/digiverso/goobi/metadata/
        // check, if a file *_db_export.xml exists in the process folder
        // return the folder names
        allFilenames = new ArrayList<>();
        allFilenames.add("Select all");
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
            try {
                Files.find(Paths.get(ProcessImportConfiguration.getImportPath()), 2, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().matches(
                        ".*_db_export.xml"))
                .forEach(p -> allFilenames.add(p.getParent().getFileName().toString()));
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/administration_dataimport.xhtml";
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
    }
}
