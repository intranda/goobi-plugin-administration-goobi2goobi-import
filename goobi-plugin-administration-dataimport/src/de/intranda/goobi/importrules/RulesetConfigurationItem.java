package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class RulesetConfigurationItem {

    // check if the old ruleset matches the configured name
    // when it is empty or null, it always matches
    private String oldRulesetName;

    // replace the old ruleset with this ruleset
    private String newRulesetName;

    // replace the old ruleset file name with this file name
    private String newFileName;

}
