package com.ex.spring.service.impl;

import com.ex.spring.annotation.EXService;
import com.ex.spring.service.DemoService;

@EXService
public class DemoServiceImpl implements DemoService{
    public void printHello() {
        System.out.println("hello world");
    }
}
