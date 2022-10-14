package org.springframework.cloud.config.monitor

import java.util.concurrent.atomic.AtomicInteger
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["\${spring.cloud.config.monitor.endpoint.path:}/monitor"])
class PropertyPathEndpoint(
    private val extractor: PropertyPathNotificationExtractor,
    private val busId: String,
) : ApplicationEventPublisherAware {

    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher
    }

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun notifyByForm(@RequestHeader headers: HttpHeaders, @RequestParam(PATH) request: List<String>): Set<String> {
        val map: MutableMap<String, Any> = HashMap()
        map[PATH] = request
        return notifyByPath(headers, map)
    }

    @PostMapping
    @Suppress("DEPRECATION")
    fun notifyByPath(@RequestHeader headers: HttpHeaders, @RequestBody request: Map<String, Any>): Set<String> {
        val notification = extractor.extract(headers, request)

        return notification?.run {
            val services = this.paths
                .flatMap { guessServiceName(it) }
                .toSet()

            services.forEach {
                applicationEventPublisher.publishEvent(RefreshRemoteApplicationEvent(this, busId, it))
            }

            services
        } ?: emptySet()
    }

    private fun guessServiceName(path: String?): Set<String> {
        val services: MutableSet<String> = LinkedHashSet()

        if (path.isNullOrEmpty()) {
            return services
        }

        val stem = path.run {
            val cleanPath = StringUtils.cleanPath(this)
            val filename = StringUtils.getFilename(cleanPath)
            filename?.let { StringUtils.stripFilenameExtension(it) }
        } ?: EMPTY

        val index = AtomicInteger(stem.indexOf(DASH))

        while (index.get() >= 0) {
            val name = stem.substring(0, index.get())
            val profile = stem.substring(index.get() + 1)
            services.addAll(getServices(name, profile))
            index.set(stem.indexOf(DASH, index.get() + 1))
        }

        if (APPLICATION == stem) {
            services.add(WILD_CARD)
            return services
        }

        if (stem.startsWith(APPLICATION)) {
            return services
        }

        services.add(stem)
        return services
    }

    private fun getServices(name: String, profile: String): Set<String> {
        val services: MutableSet<String> = HashSet()

        if (APPLICATION == name) {
            services.add(WILD_CARD + SEMI_CLONE + profile)
            return services
        }

        if (name.startsWith(APPLICATION)) {
            return services
        }

        services.add(name + SEMI_CLONE + profile)
        services.add(name)
        return services
    }

    companion object {
        private const val PATH = "path"
        private const val APPLICATION = "application"
        private const val EMPTY = ""
        private const val DASH = "-"
        private const val SEMI_CLONE = ":"
        private const val WILD_CARD = "*"
    }

}