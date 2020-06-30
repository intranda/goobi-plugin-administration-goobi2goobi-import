package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class ProcessConfigurationItem {

    // check if the process log shall be written or skipped
    private boolean skipProcesslog;

    // check if the user should be imported
    private boolean skipUserImport;



}
