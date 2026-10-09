package com.appcontrol.panel.dto;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.util.List;
public final class Models {
    private Models() {}
    public record ReleaseRule(@Min(1) long build, @NotBlank @Size(max=30) String version, @NotNull Boolean enabled) {}
    public record PlatformPolicy(@Min(1) long minimumBuild, @Min(1) long latestBuild,
        @NotBlank @Size(max=30) String version, @NotBlank @Size(max=500) String storeUrl,
        @Size(max=100) List<@NotNull @Valid ReleaseRule> releases) {
        public PlatformPolicy { releases = releases == null ? List.of() : List.copyOf(releases); }
        public PlatformPolicy(long minimumBuild,long latestBuild,String version,String storeUrl) {
            this(minimumBuild,latestBuild,version,storeUrl,List.of());
        }
    }
    public record Policy(boolean maintenance, @NotBlank @Size(max=1000) String maintenanceEn,
        @NotBlank @Size(max=1000) String maintenanceAr, @NotBlank @Size(max=1000) String updateEn,
        @NotBlank @Size(max=1000) String updateAr, @NotNull @Valid PlatformPolicy android,
        @NotNull @Valid PlatformPolicy ios) {}
    public record Registration(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,128}") @JsonAlias("id") String installationId,
        @NotBlank @Pattern(regexp="android|ios") String platform, @NotBlank @Size(max=100) String model,
        @Min(1) long build, @NotBlank @Size(max=30) @JsonAlias("deviceVersion") String version,
        @NotBlank @Pattern(regexp="en|ar") String language, @Size(max=4096) @JsonAlias("pushNotificationToken") String fcmToken) {}
    public record Heartbeat(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,128}") String installationId) {}
    public record Event(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,128}") String eventId,
        @NotBlank @Size(max=80) String feature, @NotBlank @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|LOCAL") String method,
        @NotBlank @Pattern(regexp="/[A-Za-z0-9/_{}.-]{0,180}") String path,
        @Min(100) @Max(599) int status, @Min(0) @Max(3600000) long durationMs,
        @NotNull @Size(max=1000) String message,
        @JsonAlias({"Input.json","input.json"}) JsonNode input,
        @JsonAlias({"Output.json","output.json"}) JsonNode output) {}
    public record PushItem(@NotBlank @Size(max=128) String id,
        @NotBlank @Size(max=100) String title, @NotBlank @Size(max=1000) String message) {}
    public record Push(@NotNull @Size(min=1,max=100) List<@NotBlank @Size(max=128) String> userIds,
        @NotBlank @Size(max=100) String title, @NotBlank @Size(max=1000) String body) {}
}
