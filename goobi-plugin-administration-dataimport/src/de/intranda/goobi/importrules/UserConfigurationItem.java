package de.intranda.goobi.importrules;

import java.util.List;

import lombok.Data;

@Data
public class UserConfigurationItem {

    private List<String> addProjectList;
    private List<String> removeProjectList;

    private String login;

    private String place;
    private Integer tablesize;
    private Boolean massdownload;
    private String ldapgroup;
    private String email;
    private String shortcut;
    private Boolean displayDeactivatedProjects;
    private Boolean displayFinishedProcesses;
    private Boolean displaySelectBoxes;
    private Boolean displayIdColumn;
    private Boolean displayBatchColumn;
    private Boolean displayProcessDateColumn;
    private Boolean displayLocksColumn;
    private Boolean displaySwappingColumn;
    private Boolean displayModulesColumn;
    private Boolean displayMetadataColumn;
    private Boolean displayThumbColumn;
    private Boolean displayGridView;
    private Boolean displayAutomaticTasks;
    private Boolean hideCorrectionTasks;
    private Boolean displayOnlySelectedTasks;
    private Boolean displayOnlyOpenTasks;
    private Boolean displayOtherTasks;
    private Boolean metsDisplayTitle;
    private Boolean metsLinkImage;
    private Boolean metsDisplayPageAssignments;
    private Boolean metsDisplayHierarchy;
    private Boolean metsDisplayProcessID;
    private String customColumns;
    private String customCss;
}
