/*
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 * + Copyright 2025. NHN Academy Corp. All rights reserved.
 * + * While every precaution has been taken in the preparation of this resource,  assumes no
 * + responsibility for errors or omissions, or for damages resulting from the use of the information
 * + contained herein
 * + No part of this resource may be reproduced, stored in a retrieval system, or transmitted, in any
 * + form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the
 * + prior written permission.
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

package com.nhnacademy.search;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.config.GeminiProperties;
import com.nhnacademy.search.config.ReviewSummaryPipelineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient
@EnableConfigurationProperties({ElasticsearchProperties.class, GeminiProperties.class, ReviewSummaryPipelineProperties.class, BookStatsSyncProperties.class})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }

}
