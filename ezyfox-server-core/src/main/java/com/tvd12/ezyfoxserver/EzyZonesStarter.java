package com.tvd12.ezyfoxserver;

import java.util.Map;
import java.util.Set;

import com.tvd12.ezyfoxserver.ccl.EzyAppClassLoader;
import com.tvd12.ezyfoxserver.constant.EzyEventType;
import com.tvd12.ezyfoxserver.context.EzyZoneContext;
import com.tvd12.ezyfoxserver.event.EzyServerInitializingEvent;
import com.tvd12.ezyfoxserver.event.EzySimpleServerInitializingEvent;

public class EzyZonesStarter extends EzyComponentsStater {

    protected EzyZonesStarter(Builder builder) {
        super(builder);
    }
    
    @Override
    public void start() {
        startAllZones();
    }
    
    protected void startAllZones() {
        Set<String> zoneNames = getZoneNames();
        logger.info("start zones: {}", zoneNames);
        for(String zoneName : zoneNames)
            this.startZone(zoneName);
        
    }
    
    protected void startZone(String zoneName) {
        EzyZoneContext zoneContext = serverContext.getZoneContext(zoneName);
        notifyServerInitializing(zoneContext);
        startAllPlugins(zoneContext);
        startAllApps(zoneContext);
    }
    
    protected void notifyServerInitializing(EzyZoneContext zoneContext) {
        EzyServerInitializingEvent event = new EzySimpleServerInitializingEvent();
        zoneContext.handleEvent(EzyEventType.SERVER_INITIALIZING, event);
    }
    
    //===================== plugins ===================
    protected void startAllPlugins(EzyZoneContext zoneContext) {
        logger.info("start all plugins ...");
        startComponents(newPluginsStarterBuilder(), zoneContext);
    }
    
    protected EzyPluginsStarter.Builder newPluginsStarterBuilder() {
        return EzyPluginsStarter.builder();
    }
    
    //=================================================
    
  //====================== apps ===================
    protected void startAllApps(EzyZoneContext zoneContext) {
        logger.info("start all apps ...");
        EzyAppsStarter.Builder appClassLoaders = newAppsStarterBuilder()
                .appClassLoaders(getAppClassLoaders());
        startComponents(appClassLoaders, zoneContext);
    }
    
    protected EzyAppsStarter.Builder newAppsStarterBuilder() {
        return EzyAppsStarter.builder();
    }
    //=================================================
    
    protected void startComponents(
            EzyZoneComponentsStater.Builder<?, ?> builder, EzyZoneContext zoneContext) {
        EzyZoneComponentsStater starter = newComponenstStater(builder, zoneContext);
        starter.start();
    }
    
    protected EzyZoneComponentsStater newComponenstStater(
            EzyZoneComponentsStater.Builder<?, ?> builder, EzyZoneContext zoneContext) {
        return builder
                .zoneContext(zoneContext)
                .build();
    }
    
    protected Set<String> getZoneNames() {
        return settings.getZoneNames();
    }
    
    protected Map<String, EzyAppClassLoader> getAppClassLoaders() {
        return serverContext.getServer().getAppClassLoaders();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder extends EzyComponentsStater.Builder<EzyZonesStarter, Builder> {
        
        @Override
        public EzyZonesStarter build() {
            return new EzyZonesStarter(this);
        }
    }
    
}
