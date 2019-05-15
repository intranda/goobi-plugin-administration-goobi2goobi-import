package de.intranda.goobi.importrules;

import lombok.Data;

@Data
public class ProjectConfigurationItem {

    // check if the old project matches the configured name
    // when it is empty or null, it always matches
    private String oldProjectName;

    // replace the old project with this project
    private String newProjectName;

}
