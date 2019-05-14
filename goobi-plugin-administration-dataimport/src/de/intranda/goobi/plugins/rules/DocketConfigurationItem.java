package de.intranda.goobi.plugins.rules;

import lombok.Data;

@Data
public class DocketConfigurationItem {

    // check if the old docket matches the configured name
    // when it is empty or null, it always matches
    private String oldDocketName;

    // replace the old docket with this docket
    private String newDocketName;

    // replace the old xsl file with this file
    private String newFileName;

}
