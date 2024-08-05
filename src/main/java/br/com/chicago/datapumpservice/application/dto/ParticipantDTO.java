package br.com.chicago.datapumpservice.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipantDTO {
  @JsonProperty("AuthorisationServers")
  public List<AuthorisationServers> authorisationServers;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AuthorisationServers {
    @JsonProperty("CustomerFriendlyName")
    public String customerFriendlyName;
    @JsonProperty("ApiResources")
    public List<ApiResources> apiResources;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiResources {
    @JsonProperty("ApiFamilyType")
    public String apiFamilyType;
    @JsonProperty("ApiDiscoveryEndpoints")
    public List<ApiDiscoveryEndpoint> apiDiscoveryEndpoints;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiDiscoveryEndpoint {
    @JsonProperty("ApiEndpoint")
    public String apiEndpoint;
  }
}