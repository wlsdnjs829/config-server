package org.springframework.cloud.config.monitor;

import org.springframework.cloud.bus.event.Destination;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping(path = "${spring.cloud.config.monitor.endpoint.path:}/monitor")
public class PropertyPathEndpoint implements ApplicationEventPublisherAware {

    public static final String PATH = "path";
    public static final String APPLICATION = "application";
    public static final String EMPTY = "";
    public static final String DASH = "-";
    public static final String SEMI_CLONE = ":";
    public static final String WILD_CARD = "*";
    private ApplicationEventPublisher applicationEventPublisher;

    private final PropertyPathNotificationExtractor extractor;
    private final String busId;

    public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor, String busId) {
        this.extractor = extractor;
        this.busId = busId;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @PostMapping
    @SuppressWarnings("deprecation")
    public Set<String> notifyByPath(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> request) {
        final PropertyPathNotification notification = this.extractor.extract(headers, request);

        if (Objects.isNull(notification)) {
            return Collections.emptySet();
        }


        final Set<String> services = new LinkedHashSet<>();

        for (String path : notification.getPaths()) {
            services.addAll(guessServiceName(path));
        }

        if (Objects.isNull(this.applicationEventPublisher)) {
            return Collections.emptySet();
        }

        for (String service : services) {
            this.applicationEventPublisher
                    .publishEvent(new RefreshRemoteApplicationEvent(this, this.busId, service));
        }

        return services;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Set<String> notifyByForm(@RequestHeader HttpHeaders headers, @RequestParam(PATH) List<String> request) {
        final Map<String, Object> map = new HashMap<>();
        map.put(PATH, request);
        return notifyByPath(headers, map);
    }

    private Set<String> guessServiceName(String path) {
        final Set<String> services = new LinkedHashSet<>();

        if (Objects.isNull(path)) {
            return services;
        }

        final String stem = Optional.of(path)
                .map(StringUtils::cleanPath)
                .map(StringUtils::getFilename)
                .map(StringUtils::stripFilenameExtension)
                .orElse(EMPTY);

        final AtomicInteger index = new AtomicInteger(stem.indexOf(DASH));

        while (index.get() >= 0) {
            String name = stem.substring(0, index.get());
            String profile = stem.substring(index.get() + 1);

            services.addAll(getServices(name, profile));

            index.set(stem.indexOf(DASH, index.get() + 1));
        }

        if (APPLICATION.equals(stem)) {
            services.add(WILD_CARD);
            return services;
        }

        if (!stem.startsWith(APPLICATION)) {
            services.add(stem);
            return services;
        }

        return services;
    }

    private Set<String> getServices(String name, String profile) {
        final Set<String> services = new HashSet<>();

        if (APPLICATION.equals(name)) {
            services.add(WILD_CARD + SEMI_CLONE + profile);
            return services;
        }

        if (!name.startsWith(APPLICATION)) {
            services.add(name + SEMI_CLONE + profile);
            services.add(name);
            return services;
        }

        return services;
    }

}