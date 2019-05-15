package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class PropertyConfigurationItem {

    // this field is used to check, if current rule should be used
    private String oldPropertyName;

    // this field is used to check, if the old value matches a regular expression, it always matches when blank
    private String oldPropertyValue;

    // replace the old property name with this one
    private String newPropertyName;

    // replace the value with this content
    private String newPropertyValue;

}
