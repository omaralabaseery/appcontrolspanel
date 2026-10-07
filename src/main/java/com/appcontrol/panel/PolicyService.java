package com.appcontrol.panel;
import java.net.URI;
import java.util.Map;
import static com.appcontrol.panel.Models.*;
public final class PolicyService {
    private PolicyService() {}
    public static Policy defaults() {
        return new Policy(false,"We are performing scheduled maintenance. Please try again shortly.",
            "التطبيق تحت الصيانة. يرجى المحاولة مرة أخرى بعد قليل.","A new version is available. Please update your app.",
            "يتوفر إصدار جديد. يرجى تحديث التطبيق.", new PlatformPolicy(1,1,"1.0.0","https://play.google.com/store/apps"),
            new PlatformPolicy(1,1,"1.0.0","https://apps.apple.com/"));
    }
    public static void validate(Policy policy) {
        validatePlatform(policy.android(),"play.google.com"); validatePlatform(policy.ios(),"apps.apple.com");
    }
    private static void validatePlatform(PlatformPolicy p,String host) {
        if(p.minimumBuild()>p.latestBuild()) throw new IllegalArgumentException("Minimum build cannot exceed latest build");
        URI uri;
        try { uri=URI.create(p.storeUrl()); } catch(Exception e) { throw new IllegalArgumentException("Invalid store URL"); }
        if(!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()) || uri.getUserInfo()!=null || uri.getPort()!=-1)
            throw new IllegalArgumentException("Store URL must use https://"+host);
    }
    public static Map<String,Object> evaluate(Policy p,String platform,long build,String language) {
        if (!platform.equals("android") && !platform.equals("ios")) throw new IllegalArgumentException("platform must be android or ios");
        if(build<1) throw new IllegalArgumentException("build must be positive");
        boolean ar=language.toLowerCase().startsWith("ar");
        var v=platform.equals("ios")?p.ios():p.android();
        boolean required=build<v.minimumBuild(), available=build<v.latestBuild();
        String action=p.maintenance()?"maintenance":required?"force_update":available?"optional_update":"allow";
        String message=p.maintenance()?(ar?p.maintenanceAr():p.maintenanceEn()):available?(ar?p.updateAr():p.updateEn()):"";
        return Map.of("action",action,"message",message,"language",ar?"ar":"en","updateRequired",required,
            "updateAvailable",available,"storeUrl",v.storeUrl(),"latestBuild",v.latestBuild(),"latestVersion",v.version());
    }
}
