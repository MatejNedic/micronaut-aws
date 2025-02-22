package io.micronaut.docs.function.client.aws.atnamed

import io.micronaut.function.client.FunctionClient
import jakarta.inject.Named

@FunctionClient
internal interface AnalyticsClient {
    @Named("analytics")
    fun visit(productId: String): String
}