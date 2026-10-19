package com.sheldon.flowable_demo.config;

import com.sheldon.flowable_demo.filter.CaptchaFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

//@Configuration
//@EnableWebSecurity
public class SecurityConfig {

    //密码加密器
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    //内存用户，测试用；后续可以改成数据库查询用户
    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails user1 = User.withUsername("zhangsan")
                .password(passwordEncoder().encode("123456"))
                .roles("user")
                .build();
        UserDetails user2 = User.withUsername("lisi")
                .password(passwordEncoder().encode("123456"))
                .roles("user")
                .build();
        UserDetails user3 = User.withUsername("manager")
                .password(passwordEncoder().encode("123456"))
                .roles("user","manager")
                .build();
        return new InMemoryUserDetailsManager(user1,user2,user3);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http
                .authorizeRequests()
                //静态资源放行 css
                .antMatchers("/css/**","/captcha").permitAll()
                //其余所有请求需要登录
                .anyRequest().authenticated()
                .and()
                // 把验证码过滤器 放在账号密码认证过滤器前面
                .addFilterBefore(new CaptchaFilter(), UsernamePasswordAuthenticationFilter.class)
                //自定义登录页面
                .formLogin()
                .loginPage("/login") //跳转登录页面地址
                .loginProcessingUrl("/doLogin") //security自动处理登录提交地址，不用自己写controller
                .usernameParameter("username") //表单账号name
                .passwordParameter("password") //表单密码name
                .defaultSuccessUrl("/home/index",true) //登录成功跳首页
                .permitAll()
                .and()
                //退出登录
                .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
                .and()
                .csrf().disable(); //开发阶段关闭csrf，方便表单提交；生产建议开启
        return http.build();
    }
}