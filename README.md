## ShimmerAddons  
ShimmerAddons 是一个针对 Shimmer-Noble-21.1 模组的补丁扩展，为原版模块增加实用功能。  
当前版本为 MacroProtector 添加了玩家靠近自动停用宏与自动恢复功能，让你的挂机体验更安全、更智能。  

## ✨ 功能特性  
🛡️ MacroProtector 增强  
玩家靠近保护  
新增 Range Protect 开关和 Protect range 滑块（0~100 格）。    
当其他真实玩家（排除 NPC）进入设定范围时，自动停用所有需要关闭的宏模块（如 AutoFish、Killaura、MiningBot 等）。  

自动恢复  
新增 Auto Restore 开关。开启后，当玩家离开保护范围时，自动恢复被禁用的宏模块，无需手动操作。  

禁用记录  
完全重写 disableAllMacro 方法，记录每次自动禁用的模块。  
通过 MacroProtectorAccessor 接口可调用 restoreDisabledMacros() 手动恢复。  

智能过滤  
利用 Shimmer 自带的 EntityUtil.isNPC() 自动排除村民及特殊玩家，避免误触发。  

无缝集成  
所有新设置（Range Protect、Protect range、Auto Restore）直接显示在 MacroProtector 的 GUI 中，与原版设置风格一致。  

⚔️ 原有功能保留  
IRC 拦截：防止被 !list 等命令探测。  

Odin 冲突修复：解决 /pc !warp 无法触发 Odin 组队命令的问题。  

# 📥 安装  
环境要求  
Minecraft 1.21.10  

Fabric Loader 0.18.4 或更高  

Fabric API 0.138.4+1.21.10  

必须安装的依赖模组  
Shimmer-Noble-21.1 需要以下模组同时存在，缺一不可：  

text
mods/  
├── fabric-api-0.138.4+1.21.10.jar  
├── Shimmer-Noble-21.1.jar               (Shimmer 本体)  
├── ImmediatelyFast-Fabric-1.13.5+1.21.10.jar  (Shimmer 依赖)  
├── baritone-greencat-fabric-1.21.10.jar       (Shimmer 依赖)  
├── Material-Config-1.21.10-7.jar              (Shimmer 依赖)  
└── shimmerfix-1.0.3.jar                (本补丁模组)  
安装步骤  
下载所有依赖模组，放入 .minecraft/mods 文件夹。  

从本仓库的 Releases 下载最新版 shimmerfix-1.0.3.jar，同样放入 mods 文件夹。  

启动游戏，确保 Shimmer 模组正常工作。  

在游戏中打开 Shimmer 菜单，找到 MacroProtector 模块，即可看到新增的设置项。  

#  🎮 使用方法  
启用 MacroProtector：确保模块已开启。  

开启靠近保护：勾选 Range Protect 开关。  

调整触发距离：拖动 Protect range 滑块设置距离（设为 0 可临时禁用）。  

开启自动恢复（可选）：勾选 Auto Restore 开关。  

可选提示：保持 Sound 和 System Tray 开启，在触发时获得音效和系统通知。  

当其他真实玩家进入范围时，所有需要停用的宏将自动关闭；  
玩家离开范围后，若 Auto Restore 开启，被禁用的宏将自动恢复。  

## 🔧 开发者信息  
构建  
本项目使用 Fabric Loom 构建，需要 JDK 21。  

bash
./gradlew build
生成的 jar 文件位于 build/libs/shimmerfix-1.0.3.jar。

依赖
Shimmer-Noble-21.1.jar（需手动放入 libs/ 目录）

Fabric API 0.138.4+1.21.10

贡献  
欢迎提交 Issue 或 Pull Request。如果你有新的补丁想法，也可以告诉我们。  

#  📄 许可证  
本项目采用 自定义许可证，代码公开但保留所有权利。  

✅ 允许：查看、学习、个人使用、提交 Pull Request 贡献。  

⚠️ 需经作者明确书面许可：重新分发（包括修改后发布）、商业使用。  

完整条款请参见项目根目录的 LICENSE 文件。  



感谢您的使用与支持！ 🎉
