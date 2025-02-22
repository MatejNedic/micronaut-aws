package io.micronaut.function.aws.proxy

import com.amazonaws.serverless.proxy.internal.testutils.AwsProxyRequestBuilder
import com.amazonaws.serverless.proxy.internal.testutils.MockLambdaContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.aws.LambdaApplicationContextBuilder
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import spock.lang.Issue
import spock.lang.Specification

class MicronautLambdaHandlerSpec extends Specification {
    /*
    Simple test to ensure that constructing with a builder or a fully-initialized ApplicationContext
    has no effect on the behaviour.
     */
    void "injected ApplicationContext preserves behaviour"() {
        given:
        MicronautLambdaHandler handler = new MicronautLambdaHandler(ApplicationContext.builder().properties([
                'spec.name': 'MicronautLambdaHandlerSpec'
        ]))
        ApplicationContext context = new LambdaApplicationContextBuilder()
                .properties([
                        'spec.name': 'MicronautLambdaHandlerSpec'
                ])
                .build()
                .start()
        MicronautLambdaHandler injectedHandler = new MicronautLambdaHandler(context)
        AwsProxyRequestBuilder builder = new AwsProxyRequestBuilder('/foo', HttpMethod.GET.toString())
        builder.queryString("param", "value")

        when:
        def response = handler.handleRequest(builder.build(), new MockLambdaContext())
        def injectedResponse = injectedHandler.handleRequest(builder.build(), new MockLambdaContext())

        then:
        injectedResponse.statusCode == response.statusCode
        injectedResponse.body == "value"
        response.body == "value"
        injectedResponse.headers == response.headers

        cleanup:
        if (handler != null)
            handler.close()
        if (injectedHandler != null)
            injectedHandler.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-aws/issues/868")
    void "test selected route reflects accept header"(){
        given:
        MicronautLambdaContainerHandler handler = new MicronautLambdaContainerHandler(
                ApplicationContext.builder().properties([
                        'spec.name': 'MicronautLambdaHandlerSpec',
                ])
        )

        when:
        AwsProxyRequestBuilder builder = new AwsProxyRequestBuilder('/bar/ok', HttpMethod.GET.toString())
        builder.header("Accept", MediaType.APPLICATION_JSON)

        def response = handler.proxy(builder.build(), new MockLambdaContext())

        then:
        response
        response.body == "{\"status\":\"ok\"}"

        when:
        builder = new AwsProxyRequestBuilder('/bar/ok', HttpMethod.GET.toString())
        builder.header("Accept", MediaType.TEXT_HTML)

        response = handler.proxy(builder.build(), new MockLambdaContext())

        then:
        response
        response.body == "<div>ok</div>"

        cleanup:
        handler.close()
    }

    void "test behavior of 404"() {
        given:
        MicronautLambdaContainerHandler handler = new MicronautLambdaContainerHandler(
                ApplicationContext.builder().properties([
                        'spec.name': 'MicronautLambdaHandlerSpec',
                        'micronaut.security.enabled': false
                ])
        )

        when:
        AwsProxyRequestBuilder builder = new AwsProxyRequestBuilder('/does-not-exist', HttpMethod.GET.toString())
        builder.header("Accept", MediaType.APPLICATION_JSON)

        def response = handler.proxy(builder.build(), new MockLambdaContext())

        then:
        response
        response.statusCode == 404

        cleanup:
        handler.close()
    }

    @Controller
    @Requires(property = 'spec.name', value = 'MicronautLambdaHandlerSpec')
    @Secured(SecurityRule.IS_ANONYMOUS)
    static class SimpleController {
        @Get(uri = "/foo")
        HttpResponse<String> getParamValue(HttpRequest request) {
            return HttpResponse.ok()
                    .body(request.getParameters().get("param"))
                    .header("foo", "bar")
        }
    }

    @Controller("/bar")
    @Requires(property = 'spec.name', value = 'MicronautLambdaHandlerSpec')
    @Secured(SecurityRule.IS_ANONYMOUS)
    static class ProduceController {
        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        String getOkAsJson() {
            return "{\"status\":\"ok\"}"
        }

        @Get(value = "/ok", produces = MediaType.TEXT_HTML)
        String getOkAsHtml() {
            return "<div>ok</div>"
        }
    }
}
