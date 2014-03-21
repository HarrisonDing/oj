package com.power.oj.judge;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import jodd.io.FileUtil;

import com.power.oj.core.OjConfig;
import com.power.oj.core.bean.ResultType;
import com.power.oj.solution.SolutionModel;
import com.power.oj.solution.SolutionService;

public class PojJudgeAdapter extends JudgeAdapter
{

  public boolean Compile() throws IOException
  {
    log.info(solutionModel.getSid() + " Start compiling...");
    File sourceFile = new File(new StringBuilder(5).append(workDirPath).append(File.separator).append(sourceFileName).append(".").append(language.getExt()).toString());
    FileUtil.touch(sourceFile);
    FileUtil.writeString(sourceFile, solutionModel.getSource());

    String comShellName = OjConfig.get("compileShell");
    String compileCmdName = getCompileCmd(language.getCompileCmd(), workDirPath, sourceFileName, language.getExt());
    log.info("compileCmd: " + compileCmdName);

    /*
     * execute compiler command
     */
    Process compileProcess = Runtime.getRuntime().exec(comShellName);
    OutputStream comShellOutputStream = compileProcess.getOutputStream();
    comShellOutputStream.write(compileCmdName.getBytes());
    comShellOutputStream.flush();

    BufferedReader compileErrorBufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
    long startTime = System.currentTimeMillis();
    StringBuilder sb = new StringBuilder();
    String errorOutput = "";
    while ((startTime + 10000L > System.currentTimeMillis()) && ((errorOutput = compileErrorBufferedReader.readLine()) != null))
    {
      sb.append(errorOutput).append("\n");
    }
    compileErrorBufferedReader.close();

    int ret = 0;
    try
    {
      ret = compileProcess.waitFor();
    } catch (InterruptedException e)
    {
      if (OjConfig.getDevMode())
        e.printStackTrace();
      log.warn("Compile Process is interrupted: " + e.getLocalizedMessage());
    }

    File mainProgram = new File(new StringBuilder(4).append(workDirPath).append(File.separator).append(sourceFileName).append(".").append(language.getExe()).toString());
    log.info(mainProgram.getAbsolutePath());
    boolean success = mainProgram.isFile();

    if (!success)
    {
      updateCompileError(sb.toString());
      log.warn("Compile failed, return value: " + ret);
    }

    return success;
  }

  public boolean RunProcess() throws IOException, InterruptedException
  {
    log.info(solutionModel.getSid() + " RunProcess...");
    /*
     * execute run command
     */
    Process runProcess = Runtime.getRuntime().exec(OjConfig.get("runShell"));
    OutputStream runProcessOutputStream = runProcess.getOutputStream();
    log.info("runProcess: " + OjConfig.get("runShell"));

    int numOfData = getDataFiles();

    long timeLimit = problemModel.getTimeLimit() * language.getTimeFactor() + numOfData * language.getExtTime();
    long caseTimeLimit = problemModel.getTimeLimit() * language.getTimeFactor() + language.getExtTime();
    runProcessOutputStream.write((timeLimit + "\n").getBytes());
    runProcessOutputStream.write((caseTimeLimit + "\n").getBytes());

    long memoryLimit = (problemModel.getMemoryLimit() + language.getExtMemory()) * 1024L;
    runProcessOutputStream.write((memoryLimit + "\n").getBytes());
    log.info("timeLimit: " + timeLimit);
    log.info("caseTimeLimit: " + caseTimeLimit);
    log.info("memoryLimit: " + memoryLimit);

    String mainProgram = new StringBuilder(6).append(workDirPath).append(File.separator).append(sourceFileName).append(".").append(language.getExe()).append("\n").toString();
    runProcessOutputStream.write(mainProgram.getBytes());
    runProcessOutputStream.write((workDirPath + "\n").getBytes());
    log.info("mainProgram: " + mainProgram);

    runProcessOutputStream.write((numOfData + "\n").getBytes());
    log.info("data files: " + numOfData);
    if (numOfData < 1)
    {
      log.warn("No data file for problem " + solutionModel.getPid());
    }
    for (int i = 0; i < numOfData; ++i)
    {
      runProcessOutputStream.write((inFiles.get(i) + "\n").getBytes());
      log.info(inFiles.get(i));
      String userOutFile = new StringBuilder(4).append(workDirPath).append(File.separator).append(new File(outFiles.get(i)).getName()).append("\n").toString();
      runProcessOutputStream.write(userOutFile.getBytes());
      log.info(userOutFile);
      runProcessOutputStream.write((outFiles.get(i) + "\n").getBytes());
      log.info(outFiles.get(i));
    }
    runProcessOutputStream.flush();

    BufferedReader inputStreamBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));

    String buff = inputStreamBufferedReader.readLine();
    log.info("original time: " + buff);
    int time = Integer.parseInt(buff);
    buff = inputStreamBufferedReader.readLine();
    log.info("original memory: " + buff);
    int memory = Integer.parseInt(buff);
    if (memory > 0)
      memory -= language.getExtMemory();
    StringBuilder sb = new StringBuilder();

    InputStream errorStream = runProcess.getErrorStream();
    while (errorStream.available() > 0)
    {
      Character c = new Character((char) errorStream.read());
      sb.append(c);
    }
    String errorOut = sb.toString();
    log.info("errorOut: " + errorOut);

    runProcessOutputStream.close();
    inputStreamBufferedReader.close();
    errorStream.close();

    int result = runProcess.waitFor();
    result = runProcess.exitValue();
    log.info("original result: " + result);
    if (result != ResultType.AC && (errorOut.indexOf("Exception") != -1 || errorOut.indexOf("Traceback") != -1) || errorOut.indexOf("Runtime error") != -1)
    {
      result = ResultType.RE;
    }
    if (result < 0)
    {
      result = ResultType.SE;
    }
    log.info("result: " + result + "  time: " + time + "  memory: " + memory);

    synchronized (JudgeAdapter.class)
    {
      boolean ret = updateResult(result, time, memory);
      updateUser();
      if (!updateContest())
      {
        updateProblem();
      }
      
      return ret;
    }
  }
  
  public static void main(String[] args)
  {
    for (int i = 1000; i < 1010; ++i)
    {
      SolutionModel solutionModel = SolutionService.me().findSolution(i);
      //synchronized (JudgeAdapter.class)
      {
        JudgeAdapter.addSolution(solutionModel);
        if (JudgeAdapter.size() <= 1)
        {
          JudgeAdapter judge = new PojJudgeAdapter();
          new Thread(judge).start();
        }
      }
    }
  }

}
