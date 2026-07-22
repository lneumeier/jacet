package com.example;

import java.util.List;

import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;

import de.example.util.Helper;
import de.irotation.jacet.FormatResult;

import lombok.Getter;

import picocli.CommandLine;

@Service
public class ImportDemo {

  @Getter
  private List<String> items;

  private CommandLine commandLine;
  private FormatResult formatResult;
  private ImmutableList<String> immutable;
  private Helper helper;
}
