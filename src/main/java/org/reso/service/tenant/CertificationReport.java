package org.reso.service.tenant;

 public class CertificationReport {
  private String certificationReportId;
  private String recipientUoi;
  private String providerUoi;
  private String report; // full metadata report

  // Getters and setters
  public String getCertificationReportId() { return certificationReportId; }
  public void setCertificationReportId(String certificationReportId) { this.certificationReportId = certificationReportId; }
  public String getRecipientUoi() { return recipientUoi; }
  public void setRecipientUoi(String recipientUoi) { this.recipientUoi = recipientUoi; }
  public String getProviderUoi() { return providerUoi; }
  public void setProviderUoi(String providerUoi) { this.providerUoi = providerUoi; }
  public String getReport() { return report; }
  public void setReport(String report) { this.report = report; }
}