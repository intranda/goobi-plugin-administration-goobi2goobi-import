package de.intranda.goobi.plugins.rules;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Usergroup;

import lombok.Data;

@Data
public class StepConfigurationItem {

    public enum ConfigurationType {
        CHANGE_CURRENT_STEP,
        DELETE_CURRENT_STEP,
        INSERT_BEFORE,
        INSERT_AFTER;
    };

    // this field is used to check, if configuration item matches the current step
    private String currentStepName;

    // defines if the current step should be changed, deleted or if a new step shall be added before or after current task
    private ConfigurationType configurationType = ConfigurationType.CHANGE_CURRENT_STEP;

    // don't change it, if if null, clean it if it is empty (on strings only) or overwrite value
    private String newStepName = null;
    private Integer prioritaet = null;
    private Integer reihenfolge = null;

    private Short homeverzeichnisNutzen = null;
    private Boolean typMetadaten = null;
    private Boolean typAutomatisch = null;
    private Boolean typImagesLesen = null;
    private Boolean typImagesSchreiben = null;
    private Boolean typExportDMS = null;
    private Boolean typBeimAbschliessenVerifizieren = null;
    private Boolean typBeimAnnehmenAbschliessen = null;
    private Boolean delayStep = null;
    private Boolean updateMetadataIndex = null;
    private Boolean generateDocket = null;
    private Boolean batchStep = null;
    private String stepPlugin = null;
    private String validationPlugin = null;

    private Boolean typScriptStep = null;
    private String scriptname1 = null;
    private String typAutomatischScriptpfad = null;
    private String scriptname2 = null;
    private String typAutomatischScriptpfad2 = null;
    private String scriptname3 = null;
    private String typAutomatischScriptpfad3 = null;
    private String scriptname4 = null;
    private String typAutomatischScriptpfad4 = null;
    private String scriptname5 = null;
    private String typAutomatischScriptpfad5 = null;

    private Boolean httpStep = null;
    private String httpUrl = null;
    private String httpMethod = null;
    private String httpJsonBody = null;
    private Boolean httpCloseStep = null;
    private Boolean httpEscapeBodyJson = null;

    private List<Usergroup> benutzergruppen = null;

    private Integer stepStatus = null;

    private StringBuilder stepConfigurationSummary = null;

    public String getSummary() {
        if (stepConfigurationSummary == null) {
            stepConfigurationSummary = new StringBuilder();
            stepConfigurationSummary.append("<ul class=\"popup-ul\">");

            if (StringUtils.isNotBlank(newStepName)) {
                stepConfigurationSummary.append("<li>");
                stepConfigurationSummary.append("new name: ");
                stepConfigurationSummary.append(newStepName);
                stepConfigurationSummary.append("</li>");
            }
            if (prioritaet != null) {
                stepConfigurationSummary.append("<li>");
                stepConfigurationSummary.append("new priority: ");
                stepConfigurationSummary.append(prioritaet);
                stepConfigurationSummary.append("</li>");
            }
            if (reihenfolge != null) {
                stepConfigurationSummary.append("<li>");
                stepConfigurationSummary.append("new order: ");
                stepConfigurationSummary.append(reihenfolge);
                stepConfigurationSummary.append("</li>");
            }
            if (homeverzeichnisNutzen != null || typMetadaten != null || typAutomatisch != null || typImagesLesen != null
                    || typImagesSchreiben != null || typExportDMS != null || typBeimAbschliessenVerifizieren != null || validationPlugin != null
                    || delayStep != null || updateMetadataIndex != null || generateDocket != null || batchStep != null || stepPlugin != null) {
                stepConfigurationSummary.append("<li>");

                stepConfigurationSummary.append("configured properties: ");
                if (homeverzeichnisNutzen != null) {
                    stepConfigurationSummary.append("home directory: ");
                    stepConfigurationSummary.append(homeverzeichnisNutzen);
                    stepConfigurationSummary.append("; ");
                }
                if (typMetadaten != null) {
                    stepConfigurationSummary.append("metadata: ");
                    stepConfigurationSummary.append(typMetadaten);
                    stepConfigurationSummary.append("; ");
                }
                if (typAutomatisch != null) {
                    stepConfigurationSummary.append("automatic: ");
                    stepConfigurationSummary.append(typAutomatisch);
                    stepConfigurationSummary.append("; ");
                }

                if (typImagesLesen != null) {
                    stepConfigurationSummary.append("read images: ");
                    stepConfigurationSummary.append(typImagesLesen);
                    stepConfigurationSummary.append("; ");
                }

                if (typImagesSchreiben != null) {
                    stepConfigurationSummary.append("write images: ");
                    stepConfigurationSummary.append(typImagesSchreiben);
                    stepConfigurationSummary.append("; ");
                }
                if (typExportDMS != null) {
                    stepConfigurationSummary.append("export: ");
                    stepConfigurationSummary.append(typExportDMS);
                    stepConfigurationSummary.append("; ");
                }
                if (typBeimAbschliessenVerifizieren != null) {
                    stepConfigurationSummary.append("verify: ");
                    stepConfigurationSummary.append(typBeimAbschliessenVerifizieren);
                    stepConfigurationSummary.append("; ");
                }
                if (delayStep != null) {
                    stepConfigurationSummary.append("delay: ");
                    stepConfigurationSummary.append(delayStep);
                    stepConfigurationSummary.append("; ");
                }
                if (updateMetadataIndex != null) {
                    stepConfigurationSummary.append("update index: ");
                    stepConfigurationSummary.append(updateMetadataIndex);
                    stepConfigurationSummary.append("; ");
                }

                if (generateDocket != null) {
                    stepConfigurationSummary.append("docket: ");
                    stepConfigurationSummary.append(generateDocket);
                    stepConfigurationSummary.append("; ");
                }

                if (batchStep != null) {
                    stepConfigurationSummary.append("batch: ");
                    stepConfigurationSummary.append(batchStep);
                    stepConfigurationSummary.append("; ");
                }
                if (stepPlugin != null) {
                    stepConfigurationSummary.append("stepPlugin: ");
                    stepConfigurationSummary.append(stepPlugin);
                    stepConfigurationSummary.append("; ");
                }
                if (validationPlugin != null) {
                    stepConfigurationSummary.append("validationPlugin: ");
                    stepConfigurationSummary.append(validationPlugin);
                    stepConfigurationSummary.append("; ");
                }
                stepConfigurationSummary.append("</li>");
            }
            if (typScriptStep != null) {
                stepConfigurationSummary.append("<li>");
                stepConfigurationSummary.append("script: ");
                stepConfigurationSummary.append(typScriptStep);

                if (typScriptStep) {
                    stepConfigurationSummary.append(": ");
                    if (StringUtils.isNotBlank(scriptname1)) {
                        stepConfigurationSummary.append(scriptname1);
                    }
                    if (StringUtils.isNotBlank(typAutomatischScriptpfad)) {
                        stepConfigurationSummary.append(" (");
                        stepConfigurationSummary.append(typAutomatischScriptpfad);
                        stepConfigurationSummary.append(") ");
                    }

                    if (StringUtils.isNotBlank(scriptname2)) {
                        stepConfigurationSummary.append(scriptname2);
                    }
                    if (StringUtils.isNotBlank(typAutomatischScriptpfad2)) {
                        stepConfigurationSummary.append(" (");
                        stepConfigurationSummary.append(typAutomatischScriptpfad2);
                        stepConfigurationSummary.append(") ");
                    }
                    if (StringUtils.isNotBlank(scriptname3)) {
                        stepConfigurationSummary.append(scriptname3);
                    }
                    if (StringUtils.isNotBlank(typAutomatischScriptpfad3)) {
                        stepConfigurationSummary.append(" (");
                        stepConfigurationSummary.append(typAutomatischScriptpfad3);
                        stepConfigurationSummary.append(") ");
                    }
                    if (StringUtils.isNotBlank(scriptname4)) {
                        stepConfigurationSummary.append(scriptname4);
                    }
                    if (StringUtils.isNotBlank(typAutomatischScriptpfad4)) {
                        stepConfigurationSummary.append(" (");
                        stepConfigurationSummary.append(typAutomatischScriptpfad4);
                        stepConfigurationSummary.append(") ");
                    }
                    if (StringUtils.isNotBlank(scriptname5)) {
                        stepConfigurationSummary.append(scriptname5);
                    }
                    if (StringUtils.isNotBlank(typAutomatischScriptpfad5)) {
                        stepConfigurationSummary.append(" (");
                        stepConfigurationSummary.append(typAutomatischScriptpfad5);
                        stepConfigurationSummary.append(") ");
                    }
                }

                stepConfigurationSummary.append("</li>");
            }
            if (httpStep != null) {
                stepConfigurationSummary.append("<li>");
                stepConfigurationSummary.append("httpStep: ");
                stepConfigurationSummary.append(httpStep);
                if (httpStep) {
                    stepConfigurationSummary.append(": ");
                    if (httpUrl != null) {
                        stepConfigurationSummary.append("url: ");
                        stepConfigurationSummary.append(httpUrl);
                        stepConfigurationSummary.append("; ");
                    }
                    if (httpMethod != null) {
                        stepConfigurationSummary.append("method: ");
                        stepConfigurationSummary.append(httpMethod);
                        stepConfigurationSummary.append("; ");
                    }
                    if (httpJsonBody != null) {
                        stepConfigurationSummary.append("body: ");
                        stepConfigurationSummary.append(httpJsonBody);
                        stepConfigurationSummary.append("; ");
                    }
                    if (httpCloseStep != null) {
                        stepConfigurationSummary.append("close step: ");
                        stepConfigurationSummary.append(httpCloseStep);
                        stepConfigurationSummary.append("; ");
                    }
                    if (httpEscapeBodyJson != null) {
                        stepConfigurationSummary.append("escape body: ");
                        stepConfigurationSummary.append(httpEscapeBodyJson);
                        stepConfigurationSummary.append("; ");
                    }
                }

                stepConfigurationSummary.append("</li>");
            }


            //
            stepConfigurationSummary.append("</ul>");
        }

        return stepConfigurationSummary.toString();
    }
}
