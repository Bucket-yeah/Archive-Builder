import { Router, type IRouter } from "express";
import path from "path";
import fs from "fs";

const router: IRouter = Router();

const JAR_PATH = "/home/runner/workspace/chaos_addon_output/build/libs/chaos_addon-2.0.0.jar";

router.get("/download/chaos_addon.jar", (_req, res) => {
  if (!fs.existsSync(JAR_PATH)) {
    res.status(404).json({ error: "Jar not found", path: JAR_PATH });
    return;
  }

  res.setHeader("Content-Disposition", "attachment; filename=chaos_addon-2.0.0.jar");
  res.setHeader("Content-Type", "application/java-archive");
  res.sendFile(JAR_PATH);
});

export default router;
