package com.malai.security

import com.malai.commons.exception.ForbiddenException
import com.malai.commons.exception.MalaiCode.GENERIC_FORBIDDEN
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

class MalaiUserWithAccountHolderIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == MalaiUserWithAccountHolderId::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): MalaiUserWithAccountHolderId {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return principal as? MalaiUserWithAccountHolderId
            ?: throw ForbiddenException(GENERIC_FORBIDDEN, "request carries no account-holder context")
    }
}

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(0)
class MalaiSecurityWebMvcConfigurer : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(MalaiUserWithAccountHolderIdArgumentResolver())
    }
}
