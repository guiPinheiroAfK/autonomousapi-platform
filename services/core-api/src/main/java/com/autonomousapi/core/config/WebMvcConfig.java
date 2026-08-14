package com.autonomousapi.core.config;

import com.autonomousapi.core.billing.SubscriptionGate;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SubscriptionGate subscriptionGate;

    public WebMvcConfig(SubscriptionGate subscriptionGate) {
        this.subscriptionGate = subscriptionGate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /v1/auth/**: signup/login não têm tenant ainda. /v1/billing/**: precisa
        // continuar funcionando exatamente quando a assinatura está vencida — é como o
        // tenant sai do bloqueio. Excluir os dois é o que evita o tenant ficar preso
        // sem conseguir nem checkout.
        registry.addInterceptor(subscriptionGate)
                .excludePathPatterns("/v1/auth/**", "/v1/billing/**");
    }
}
