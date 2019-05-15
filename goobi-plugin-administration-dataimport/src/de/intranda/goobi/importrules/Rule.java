package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import de.intranda.goobi.importrules.DocketConfigurationItem;
import de.intranda.goobi.importrules.MetadataConfigurationItem;
import de.intranda.goobi.importrules.ProjectConfigurationItem;
import de.intranda.goobi.importrules.PropertyConfigurationItem;
import de.intranda.goobi.importrules.RulesetConfigurationItem;
import de.intranda.goobi.importrules.StepConfigurationItem;
import lombok.Data;

@Data
public class Rule {

    // name of the rule, can be selected from a list of all rules
    private String rulename;

    // list of all manipulations on the workflow
    private List<StepConfigurationItem> configuredStepRules = new ArrayList<>();

    // list of all manipulations for properties
    private List<PropertyConfigurationItem> configuredPropertyRules = new ArrayList<>();

    // list of all manipulations of metadata
    private List<MetadataConfigurationItem> configuredMetadataRules = new ArrayList<>();

    private List<RulesetConfigurationItem> configuredRulesetRules = new ArrayList<>();

    private List<DocketConfigurationItem> configuredDocketRules = new ArrayList<>();

    private List<ProjectConfigurationItem> configuredProjectRules = new ArrayList<>();



    public int getConfiguredStepRulesSize() {
        return configuredStepRules.size();
    }
    public int getConfiguredPropertyRulesSize() {
        return configuredPropertyRules.size();
    }
    public int getConfiguredMetadataRulesSize() {
        return configuredMetadataRules.size();
    }
    public int getConfiguredRulesetRulesSize() {
        return configuredRulesetRules.size();
    }
    public int getConfiguredDocketRulesSize() {
        return configuredDocketRules.size();
    }
    public int getConfiguredProjectRulesSize() {
        return configuredProjectRules.size();
    }

    private boolean stepPanelExpanded;
    private boolean propertyPanelExpanded;
    private boolean metadataPanelExpanded;
    private boolean rulesetPanelExpanded;
    private boolean docketPanelExpanded;
    private boolean projectPanelExpanded;


}
