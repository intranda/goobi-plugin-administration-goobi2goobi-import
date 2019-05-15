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
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import de.intranda.goobi.importrules.ImportConfiguration;
import de.intranda.goobi.importrules.Rule;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class ImportProcessDatebasePlugin implements IAdministrationPlugin {

    @Getter
    private String title = "goobi-plugin-administration-database-information";

    //    //TODO: make these two configurable
    private String importPath = "/opt/digiverso/goobi/metadata/";
    private String bucket = "goobi-db-export-bucket";

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
        return ImportConfiguration.getConfiguredItems();
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

            importTicket.setProcessName(processId);

            //TODO check S3 path
            Path processFolder = Paths.get(importPath, processId);

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

        Helper.setMeldung(Helper.getTranslation("plugin_adiministration_dataimport_success", ""+selectedFilenames.size())) ;
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
            AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
            ListObjectsRequest req = new ListObjectsRequest().withBucketName(bucket).withPrefix(importPath);
            ObjectListing listing = s3.listObjects(req);
            for (S3ObjectSummary os : listing.getObjectSummaries()) {
                String key = os.getKey();
                allFilenames.add(key);
            }
            while (listing.isTruncated()) {
                listing = s3.listNextBatchOfObjects(listing);
                for (S3ObjectSummary os : listing.getObjectSummaries()) {
                    String key = os.getKey();
                    allFilenames.add(key);
                }
            }
        } else {
            try {
                Files.find(Paths.get(importPath), 2, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().matches(".*_db_export.xml"))
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
            for (Rule rule : ImportConfiguration.getConfiguredItems()) {
                allRulenames.add(rule.getRulename());
            }

        }

        return allRulenames;
    }


    public void reloadRules() {
        ImportConfiguration.resetRules();
    }
}
