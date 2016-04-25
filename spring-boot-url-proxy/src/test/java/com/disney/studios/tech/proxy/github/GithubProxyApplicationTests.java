package com.disney.studios.tech.proxy.github;

import com.github.ulisesbocchio.spring.boot.proxy.GithubProxyApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = GithubProxyApplication.class)
@WebAppConfiguration
public class GithubProxyApplicationTests {

	@Test
	public void contextLoads() {
	}

}
