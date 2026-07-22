package com.example;

import picocli.CommandLine;
import lombok.Getter;
import de.irotation.jacet.FormatResult;
import org.springframework.stereotype.Service;
import com.google.common.collect.ImmutableList;
import de.example.util.Helper;
import java.util.List;

@Service
public class ImportDemo {

  @Getter
  private List<String> items;

  private CommandLine commandLine;
  private FormatResult formatResult;
  private ImmutableList<String> immutable;
  private Helper helper;
}
