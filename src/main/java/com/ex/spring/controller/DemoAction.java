package com.ex.spring.controller;

import com.ex.spring.annotation.EXAutowire;
import com.ex.spring.annotation.EXController;
import com.ex.spring.annotation.EXRequestMapping;
import com.ex.spring.annotation.EXRequestParam;
import com.ex.spring.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@EXController
@EXRequestMapping("demo")
public class DemoAction {
    @EXAutowire
    private DemoService demoService;

    @EXRequestMapping("query")
    public void query(HttpServletRequest request, HttpServletResponse response,@EXRequestParam("name")String name){
        try {
            demoService.printHello();
            response.getWriter().write(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
