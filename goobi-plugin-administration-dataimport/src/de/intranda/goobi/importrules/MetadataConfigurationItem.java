package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class MetadataConfigurationItem {

    public enum MetadataConfigurationType {
        CHANGE_CURRENT_METADATA,
        DELETE_CURRENT_METADATA,
        ADD_NEW_METADATA
    };

    // internal metadata name
    private String metadataName;

    private MetadataConfigurationType configurationType = MetadataConfigurationType.CHANGE_CURRENT_METADATA;

    // execute the rule only if this field is empty or the value matches the expression
    private String valueContitionRegex;

    // regular expression to manipulate the value
    private String valueReplacementRegex;

    // all, anchor, top, physical
    private String position;

}
