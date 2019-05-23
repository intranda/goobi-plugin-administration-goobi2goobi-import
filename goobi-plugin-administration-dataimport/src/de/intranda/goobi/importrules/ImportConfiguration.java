package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;

import de.intranda.goobi.importrules.MetadataConfigurationItem.MetadataConfigurationType;
import de.intranda.goobi.importrules.StepConfigurationItem.ConfigurationType;
import de.sub.goobi.config.ConfigPlugins;

/**
 * read the configuration file
 * 
 */

public class ImportConfiguration {

    private static List<Rule> configuredRules = new ArrayList<>();
    private static XMLConfiguration config = null;

    //    public static void main(String[] args) {
    //        List<Rule> map = getConfiguredItems();
    //        for (Rule project : map) {
    //            System.out.println("**************");
    //            System.out.println(project.getRulename());
    //            System.out.println("**************");
    //
    //            for (StepConfigurationItem sci : project.getConfiguredStepRules()) {
    //                System.out.println(sci.getCurrentStepName());
    //            }
    //            System.out.println("**************");
    //            System.out.println("Dockets");
    //
    //            for (DocketConfigurationItem dci : project.getConfiguredDocketRules()) {
    //                System.out.println("**************");
    //                System.out.println(dci.getOldDocketName());
    //                System.out.println(dci.getNewDocketName());
    //                System.out.println(dci.getNewFileName());
    //            }
    //            System.out.println("**************");
    //            System.out.println("projects");
    //
    //            for (ProjectConfigurationItem dci : project.getConfiguredProjectRules()) {
    //                System.out.println("**************");
    //                System.out.println(dci.getOldProjectName());
    //                System.out.println(dci.getNewProjectName());
    //            }
    //
    //            System.out.println("Metadata:");
    //            for (MetadataConfigurationItem mci : project.getConfiguredMetadataRules()) {
    //                System.out.println(mci.getMetadataName());
    //            }
    //
    //        }
    //    }

    private static void loadConfig() {
        config = ConfigPlugins.getPluginConfig("goobi-plugin-administration-database-information");
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        config.setExpressionEngine(new XPathExpressionEngine());
    }

    @SuppressWarnings("unchecked")
    public static List<Rule> getConfiguredItems() {

        if (configuredRules.isEmpty()) {

            //        Map<String, List<StepConfigurationItem>> answer = new HashMap<>();
            if (config == null) {
                loadConfig();
            }

            List<String> configuredProjectNames = config.getList("/config/rulename");
            for (String projectName : configuredProjectNames) {
                Rule rule = new Rule();
                rule.setRulename(projectName);

                List<StepConfigurationItem> stepList = new ArrayList<>();
                //            answer.put(projectName, stepList);
                rule.setConfiguredStepRules(stepList);
                configuredRules.add(rule);

                // read step configuration
                List<HierarchicalConfiguration> list = config.configurationsAt("/config[./rulename='" + projectName + "']/step");

                for (HierarchicalConfiguration stepConfiguration : list) {
                    String oldStepName = stepConfiguration.getString("./@name");
                    if (StringUtils.isBlank(oldStepName)) {
                        continue;
                    }
                    StepConfigurationItem stepConfigurationItem = new StepConfigurationItem();
                    stepConfigurationItem.setCurrentStepName(oldStepName);
                    stepList.add(stepConfigurationItem);

                    String type = stepConfiguration.getString("./@type", "change");
                    if ("change".equalsIgnoreCase(type)) {
                        stepConfigurationItem.setConfigurationType(ConfigurationType.CHANGE_CURRENT_STEP);
                    } else if ("delete".equalsIgnoreCase(type)) {
                        stepConfigurationItem.setConfigurationType(ConfigurationType.DELETE_CURRENT_STEP);
                    } else if ("insertBefore".equalsIgnoreCase(type)) {
                        stepConfigurationItem.setConfigurationType(ConfigurationType.INSERT_BEFORE);
                    } else if ("insertAfter".equalsIgnoreCase(type)) {
                        stepConfigurationItem.setConfigurationType(ConfigurationType.INSERT_AFTER);
                    }

                    stepConfigurationItem.setNewStepName(stepConfiguration.getString("./newStepName", null));

                    stepConfigurationItem.setPrioritaet(stepConfiguration.getInteger("./priority", null));

                    stepConfigurationItem.setReihenfolge(stepConfiguration.getInteger("./order", null));

                    stepConfigurationItem.setStepStatus(stepConfiguration.getInteger("./stepStatus", null));

                    Short useHomeDirectory = stepConfiguration.getShort("./useHomeDirectory", null);
                    stepConfigurationItem.setHomeverzeichnisNutzen(useHomeDirectory);

                    stepConfigurationItem.setTypMetadaten(stepConfiguration.getBoolean("./types/@metadata", null));
                    stepConfigurationItem.setTypAutomatisch(stepConfiguration.getBoolean("./types/@automatic", null));
                    stepConfigurationItem.setTypImagesLesen(stepConfiguration.getBoolean("./types/@readImages", null));
                    stepConfigurationItem.setTypImagesSchreiben(stepConfiguration.getBoolean("./types/@writeImages", null));
                    stepConfigurationItem.setTypExportDMS(stepConfiguration.getBoolean("./types/@export", null));
                    stepConfigurationItem.setTypBeimAbschliessenVerifizieren(stepConfiguration.getBoolean("./types/@validateOnExit", null));

                    stepConfigurationItem.setTypBeimAnnehmenAbschliessen(stepConfiguration.getBoolean("./types/@finalizeOnAccept", null));
                    stepConfigurationItem.setDelayStep(stepConfiguration.getBoolean("./types/@delayStep", null));

                    stepConfigurationItem.setUpdateMetadataIndex(stepConfiguration.getBoolean("./types/@updateMetadataIndex", null));
                    stepConfigurationItem.setGenerateDocket(stepConfiguration.getBoolean("./types/@generateDocket", null));

                    stepConfigurationItem.setBatchStep(stepConfiguration.getBoolean("./types/@batchStep", null));

                    stepConfigurationItem.setStepPlugin(stepConfiguration.getString("./types/@stepPlugin", null));
                    stepConfigurationItem.setValidationPlugin(stepConfiguration.getString("./types/@validationPlugin", null));

                    Boolean isScriptStep = stepConfiguration.getBoolean("./scriptStep/@scriptStep", null);
                    stepConfigurationItem.setTypScriptStep(isScriptStep);
                    if (isScriptStep != null && isScriptStep) {
                        stepConfigurationItem.setScriptname1(stepConfiguration.getString("./scriptStep/@scriptName1"));
                        stepConfigurationItem.setTypAutomatischScriptpfad(stepConfiguration.getString("./scriptStep/@scriptPath1"));

                        stepConfigurationItem.setScriptname2(stepConfiguration.getString("./scriptStep/@scriptName2"));
                        stepConfigurationItem.setTypAutomatischScriptpfad2(stepConfiguration.getString("./scriptStep/@scriptPath2"));

                        stepConfigurationItem.setScriptname3(stepConfiguration.getString("./scriptStep/@scriptName3"));
                        stepConfigurationItem.setTypAutomatischScriptpfad3(stepConfiguration.getString("./scriptStep/@scriptPath3"));

                        stepConfigurationItem.setScriptname5(stepConfiguration.getString("./scriptStep/@scriptName4"));
                        stepConfigurationItem.setTypAutomatischScriptpfad4(stepConfiguration.getString("./scriptStep/@scriptPath4"));

                        stepConfigurationItem.setScriptname5(stepConfiguration.getString("./scriptStep/@scriptName5"));
                        stepConfigurationItem.setTypAutomatischScriptpfad5(stepConfiguration.getString("./scriptStep/@scriptPath5"));
                    }

                    Boolean isHttpStep = stepConfiguration.getBoolean("./httpStep/@httpStep", null);
                    stepConfigurationItem.setHttpStep(isHttpStep);
                    if (isHttpStep != null && isHttpStep) {
                        stepConfigurationItem.setHttpUrl(stepConfiguration.getString("./httpStep/@httpUrl"));
                        stepConfigurationItem.setHttpMethod(stepConfiguration.getString("./httpStep/@httpMethod"));
                        stepConfigurationItem.setHttpJsonBody(stepConfiguration.getString("./httpStep/@httpJsonBody"));
                        stepConfigurationItem.setHttpCloseStep(stepConfiguration.getBoolean("./httpStep/@httpCloseStep", null));
                        stepConfigurationItem.setHttpEscapeBodyJson(stepConfiguration.getBoolean("./httpStep/@httpEscapeBodyJson", null));

                    }
                }

                // read docket configuration
                List<HierarchicalConfiguration> docketList = config.configurationsAt("/config[./rulename='" + projectName + "']/docket");
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

                // read project configuration
                List<HierarchicalConfiguration> projectList = config.configurationsAt("/config[./rulename='" + projectName + "']/project");
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
                    }
                }

                // read property configuration
                List<HierarchicalConfiguration> propertyList = config.configurationsAt("/config[./rulename='" + projectName + "']/property");
                List<PropertyConfigurationItem> configuredPropertyRules = new ArrayList<>();
                rule.setConfiguredPropertyRules(configuredPropertyRules);

                if (propertyList != null && !propertyList.isEmpty()) {
                    for (HierarchicalConfiguration propertyConfig : propertyList) {
                        PropertyConfigurationItem pci = new PropertyConfigurationItem();
                        pci.setOldPropertyName(propertyConfig.getString("./@name", null));
                        pci.setOldPropertyValue(propertyConfig.getString("./oldPropertyValue", null));
                        pci.setNewPropertyName(propertyConfig.getString("./newPropertyName", null));
                        pci.setNewPropertyValue(propertyConfig.getString("./newPropertyValue", null));
                        configuredPropertyRules.add(pci);
                    }
                }

                // read ruleset configuration
                List<HierarchicalConfiguration> rulesetList = config.configurationsAt("/config[./rulename='" + projectName + "']/ruleset");
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

                // read metadata configuration

                List<HierarchicalConfiguration> metadataList = config.configurationsAt("/config[./rulename='" + projectName + "']/metadata");
                List<MetadataConfigurationItem> configuredMetadataRules = new ArrayList<>();
                rule.setConfiguredMetadataRules(configuredMetadataRules);

                if (metadataList != null && !metadataList.isEmpty()) {
                    for (HierarchicalConfiguration metadataConfiguration : metadataList) {
                        MetadataConfigurationItem mci = new MetadataConfigurationItem();
                        mci.setMetadataName(metadataConfiguration.getString("./@name"));
                        String type = metadataConfiguration.getString("./@type", "change");
                        if ("change".equalsIgnoreCase(type)) {
                            mci.setConfigurationType(MetadataConfigurationType.CHANGE_CURRENT_METADATA);
                        } else if ("delete".equalsIgnoreCase(type)) {
                            mci.setConfigurationType(MetadataConfigurationType.DELETE_CURRENT_METADATA);
                        } else if ("add".equalsIgnoreCase(type)) {
                            mci.setConfigurationType(MetadataConfigurationType.ADD_NEW_METADATA);
                        }
                        mci.setValueContitionRegex(metadataConfiguration.getString("./valueContitionRegex"));
                        mci.setValueReplacementRegex(metadataConfiguration.getString("./valueReplacementRegex"));
                        mci.setPosition(metadataConfiguration.getString("./position", "all"));
                        configuredMetadataRules.add(mci);
                    }

                }
            }
        }
        return configuredRules;
    }

    public static void resetRules() {
        configuredRules.clear();
    }

    public static String getDbExportPrefix() {
        if (config == null) {
            loadConfig();
        }
        return config.getString("globalConfig/dbExportPrefix");
    }

    public static String getImportPath() {
        if (config == null) {
            loadConfig();
        }
        return config.getString("globalConfig/importPath");
    }

    public static String getBucket() {
        if (config == null) {
            loadConfig();
        }
        return config.getString("globalConfig/bucket");
    }
}
