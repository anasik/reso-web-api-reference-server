package org.reso.service.tenant;

import java.util.List;

 public class ServerConfig {
  private String clientId;
  private String sandboxServerId;
  private String certificationReportId;
  private String authenticationType;
  private List<String> additionalEndorsements;
  private String lookupType;

  public String getUniqueServerConfigName() {
    return certificationReportId + "-" + lookupType;
  }

  // Getters and setters
  public String getClientId() {
      return clientId;
  }
  public void setClientId(String clientId) {
      this.clientId = clientId;
  }
  public String getSandboxServerId() {
      return sandboxServerId;
  }
  public void setSandboxServerId(String sandboxServerId) {
      this.sandboxServerId = sandboxServerId;
  }
  public String getCertificationReportId() {
      return certificationReportId;
  }
  public void setCertificationReportId(String certificationReportId) {
      this.certificationReportId = certificationReportId;
  }
  public String getAuthenticationType() {
      return authenticationType;
  }
  public void setAuthenticationType(String authenticationType) {
      this.authenticationType = authenticationType;
  }
  public List<String> getAdditionalEndorsements() {
      return additionalEndorsements;
  }
  public void setAdditionalEndorsements(List<String> additionalEndorsements) {
      this.additionalEndorsements = additionalEndorsements;
  }
  public String getLookupType() {
      return lookupType;
  }
  public void setLookupType(String lookupType) {
      this.lookupType = lookupType;
  }
}