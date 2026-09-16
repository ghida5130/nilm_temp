package com.nilm.monitoring.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
@Configuration
public class KafkaConsumerConfig {
    // 미등록 가구·잘못된 메시지를 성공 처리해 버리지 않고 중지한다.
    // 원인을 수정한 후 재시작하면 마지막 커밋 위치부터 다시 처리한다.
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new CommonContainerStoppingErrorHandler();
    }
}

