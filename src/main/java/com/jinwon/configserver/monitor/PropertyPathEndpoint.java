package com.jinwon.configserver.monitor;

import org.springframework.cloud.bus.event.Destination;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.cloud.config.monitor.PropertyPathNotificationExtractor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping(path = "${spring.cloud.config.monitor.endpoint.path:}/monitor")
public class PropertyPathEndpoint implements ApplicationEventPublisherAware {

    private final PropertyPathNotificationExtractor extractor;
    private final Destination.Factory destinationFactory;
    private final String busId;

    private ApplicationEventPublisher applicationEventPublisher;

    public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor, String busId,
                                Destination.Factory destinationFactory) {
        this.extractor = extractor;
        this.busId = busId;
        this.destinationFactory = destinationFactory;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @PostMapping
    public Set<Destination> notifyByPath(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> request) {
        PropertyPathNotification notification = this.extractor.extract(headers, request);

        if (Objects.isNull(notification)) {
            return Collections.emptySet();
        }


        final Set<Destination> services = new LinkedHashSet<>();

        for (String path : notification.getPaths()) {
            services.addAll(guessServiceName(path));
        }

        if (Objects.isNull(this.applicationEventPublisher)) {
            return Collections.emptySet();
        }

        for (Destination destination : services) {
            this.applicationEventPublisher
                    .publishEvent(new RefreshRemoteApplicationEvent(this, this.busId, destination));
        }

        return services;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Set<Destination> notifyByForm(@RequestHeader HttpHeaders headers, @RequestParam("path") List<String> request) {
        Map<String, Object> map = new HashMap<>();
        String key = "path";
        map.put(key, request);
        return notifyByPath(headers, map);
    }

    private Set<Destination> guessServiceName(String path) {
        Set<Destination> services = new LinkedHashSet<>();
        if (path != null) {
            String stem = StringUtils.stripFilenameExtension(StringUtils.getFilename(StringUtils.cleanPath(path)));
            // TODO: correlate with service registry
            int index = stem.indexOf("-");
            while (index >= 0) {
                String name = stem.substring(0, index);
                String profile = stem.substring(index + 1);
                if ("application".equals(name)) {
                    services.add(getDestination("*:" + profile));
                } else if (!name.startsWith("application")) {
                    services.add(getDestination(name + ":" + profile));
                    services.add(getDestination(name));
                }
                index = stem.indexOf("-", index + 1);
            }
            String name = stem;
            if ("application".equals(name)) {
                services.add(getDestination("*"));
            } else if (!name.startsWith("application")) {
                services.add(getDestination(name));
            }
        }
        return services;
    }

    protected Destination getDestination(String original) {
        return destinationFactory.getDestination(original);
    }

}