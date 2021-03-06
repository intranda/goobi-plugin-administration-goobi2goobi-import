package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Rule {

    // name of the rule, can be selected from a list of all rules
    private String rulename;

    private boolean stepPanelExpanded;
    private boolean propertyPanelExpanded;
    private boolean metadataPanelExpanded;
    private boolean rulesetPanelExpanded;
    private boolean docketPanelExpanded;
    private boolean projectPanelExpanded;
    private boolean ldapPanelExpanded;
    private boolean usergroupPanelExpanded;
    private boolean userPanelExpanded;


    // list of all manipulations on the workflow
    private List<StepConfigurationItem> configuredStepRules = new ArrayList<>();

    // list of all manipulations for properties
    private List<PropertyConfigurationItem> configuredPropertyRules = new ArrayList<>();

    // list of all manipulations of metadata
    private List<MetadataConfigurationItem> configuredMetadataRules = new ArrayList<>();

    private List<RulesetConfigurationItem> configuredRulesetRules = new ArrayList<>();

    private List<DocketConfigurationItem> configuredDocketRules = new ArrayList<>();

    private List<ProjectConfigurationItem> configuredProjectRules = new ArrayList<>();

    private List<LdapConfigurationItem> configuredLdapRules = new ArrayList<>();

    private List<UsergroupConfigurationItem> configuredUsergroupRules = new ArrayList<>();

    private List<UserConfigurationItem> configuredUserRules = new ArrayList<>();

    private ProcessConfigurationItem processRule = new ProcessConfigurationItem();

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

    public int getConfiguredLdapRulesSize() {
        return configuredLdapRules.size();
    }

    public int getConfiguredUsergroupRulesSize() {
        return configuredUsergroupRules.size();
    }

    public int getConfiguredUserRulesSize() {
        return configuredUserRules.size();
    }

}
