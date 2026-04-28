package com.buter.test_java.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping
    public HelloResponse hello() {
        return new HelloResponse("Hello");
    }

    @GetMapping("/{name}")
    public HelloResponse helloByName(@PathVariable String name) {
        return new HelloResponse("Hello, " + name + "!");
    }

    @PostMapping
    public HelloResponse helloPost(@RequestBody @Valid HelloRequest request) {
        return new HelloResponse("Hello, " + request.name() + "!");
    }

}