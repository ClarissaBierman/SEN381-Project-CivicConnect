package com.civicconnect.common.config;

import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.DbSession;
import com.civicconnect.common.security.DbSessionInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Applies DbSessionInterceptor to every @Service method.
 *
 * Order matters: the database session settings are transaction-local, so the interceptor must run
 * INSIDE the transaction. Transactions are given the highest priority (outermost, order 0) and this
 * advisor the lowest, so the transaction is always open by the time the interceptor runs.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class DbSessionConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor dbSessionAdvisor(ObjectProvider<DbSession> db, ObjectProvider<CurrentUserService> currentUser) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                new AnnotationMatchingPointcut(Service.class, true), new DbSessionInterceptor(db, currentUser));
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }
}
