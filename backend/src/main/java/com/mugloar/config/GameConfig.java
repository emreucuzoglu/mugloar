package com.mugloar.config;

import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.gateway.client.MugloarRetry;
import com.mugloar.gateway.client.RequestPacer;
import com.mugloar.solver.strategy.BalancedShoppingStrategy;
import com.mugloar.solver.strategy.QuestSelector;
import com.mugloar.solver.strategy.RiskAwareQuestSelector;
import com.mugloar.solver.strategy.ShoppingStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MugloarProperties.class)
class GameConfig {

  @Bean
  MugloarClient mugloarClient(RestClient.Builder builder, MugloarProperties props) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(props.connectTimeout());
    factory.setReadTimeout(props.readTimeout());
    var rest = builder.baseUrl(props.baseUrl().toString()).requestFactory(factory).build();
    return new MugloarClient(
        rest,
        MugloarRetry.forReads(props.readAttempts()),
        new RequestPacer(props.minRequestInterval()));
  }

  @Bean
  QuestSelector questSelector(MugloarProperties props) {
    return new RiskAwareQuestSelector(props.selector());
  }

  @Bean
  ShoppingStrategy shoppingStrategy(MugloarProperties props) {
    return new BalancedShoppingStrategy(props.shopping());
  }
}
