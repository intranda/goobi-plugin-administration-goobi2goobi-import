package de.intranda.goobi.importrules;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ProjectConfigurationItem {

    // check if the old project matches the configured name
    // when it is empty or null, it always matches
    private String oldProjectName;

    // replace the old project with this project
    private String newProjectName;

    private List<FilegroupConfigurationItem> filegroups = new ArrayList<>();

    public FilegroupConfigurationItem createNewFilgroupItem() {
        return new FilegroupConfigurationItem();
    }

    private String fileFormatInternal;

    private String fileFormatDmsExport;

    // export configuration
    private Boolean useDmsImport;
    private Boolean dmsImportCreateProcessFolder;
    private Integer dmsImportTimeOut;
    private String dmsImportRootPath;
    private String dmsImportImagesPath;
    private String dmsImportSuccessPath;
    private String dmsImportErrorPath;

    // mets configuration
    private String metsRightsOwnerLogo;
    private String metsRightsOwnerSite;
    private String metsRightsOwnerMail;
    private String metsDigiprovReference;
    private String metsDigiprovPresentation;
    private String metsDigiprovReferenceAnchor;
    private String metsPointerPath;
    private String metsPointerPathAnchor;
    private String metsPurl;
    private String metsContentIDs;
    private String metsRightsSponsor;
    private String metsRightsSponsorLogo;
    private String metsRightsSponsorSiteURL;
    private String metsRightsLicense;

    @Data
    public class FilegroupConfigurationItem {

        private String oldName;
        private String newName;
        private String path;
        private String mimeType;
        private String fileSuffix;
        private String folderValidation;

    }
}
