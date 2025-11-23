# Contributing to DHT Spider

[English](#english) | [中文](#中文)

---

<a name="english"></a>

## 🌐 English

Thank you for your interest in contributing to DHT Spider! We welcome contributions from the community.

### 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)

### 📜 Code of Conduct

This project and everyone participating in it is governed by our commitment to creating a welcoming and harassment-free experience for everyone. Please be respectful and constructive in all interactions.

### 🤝 How Can I Contribute?

#### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- Clear and descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Environment details (OS, Java version, etc.)
- Logs and error messages

#### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- Clear and descriptive title
- Detailed description of the proposed feature
- Explain why this enhancement would be useful
- Possible implementation approach (optional)

#### Pull Requests

1. Fork the repository
2. Create a new branch from `main`
3. Make your changes
4. Write or update tests as needed
5. Ensure all tests pass
6. Update documentation if needed
7. Submit a pull request

### 🛠️ Development Setup

1. **Prerequisites**
   - Java 21 or higher
   - Maven 3.6 or higher
   - Docker and Docker Compose (for running middleware)

2. **Clone the repository**
   ```bash
   git clone https://github.com/lihongjie0209/dht-spider-java.git
   cd dht-spider-java
   ```

3. **Start middleware**
   ```bash
   docker-compose up -d redpanda redis postgres console
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run tests**
   ```bash
   mvn test
   ```

### 🔄 Pull Request Process

1. Update the README.md with details of changes if applicable
2. Update documentation for any changed APIs or configurations
3. The PR will be merged once you have the sign-off of at least one maintainer

### 📝 Coding Standards

- Follow Java naming conventions
- Write clear, self-documenting code
- Add comments for complex logic
- Keep methods focused and concise
- Write unit tests for new features
- Ensure code passes all existing tests
- Use meaningful commit messages

#### Commit Message Format

```
<type>: <subject>

<body>

<footer>
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

Example:
```
feat: Add support for IPv6 DHT nodes

- Implement IPv6 address parsing
- Add configuration for IPv6 binding
- Update tests to cover IPv6 scenarios

Closes #123
```

### 🧪 Testing Guidelines

- Write unit tests for all new features
- Ensure integration tests pass
- Test edge cases and error conditions
- Maintain or improve code coverage

### 📚 Documentation

- Update relevant documentation for any changes
- Include docstrings for public APIs
- Update README.md for significant changes
- Add code comments for complex logic

### ❓ Questions?

Feel free to open an issue for any questions or concerns.

---

<a name="中文"></a>

## 🌐 中文

感谢您对 DHT Spider 项目的关注！我们欢迎来自社区的贡献。

### 📋 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
- [开发环境设置](#开发环境设置)
- [Pull Request 流程](#pull-request-流程)
- [编码规范](#编码规范)

### 📜 行为准则

本项目及其所有参与者承诺为每个人创造一个友好和无骚扰的体验。请在所有互动中保持尊重和建设性。

### 🤝 如何贡献

#### 报告 Bug

在创建 bug 报告之前，请检查现有的 issue 以避免重复。创建 bug 报告时，请包括：

- 清晰描述性的标题
- 重现问题的步骤
- 预期行为
- 实际行为
- 环境详情（操作系统、Java 版本等）
- 日志和错误信息

#### 建议功能增强

功能增强建议作为 GitHub issue 进行跟踪。创建增强建议时，请包括：

- 清晰描述性的标题
- 提议功能的详细描述
- 解释为什么这个增强会有用
- 可能的实现方法（可选）

#### Pull Request

1. Fork 仓库
2. 从 `main` 创建新分支
3. 进行修改
4. 根据需要编写或更新测试
5. 确保所有测试通过
6. 如需要更新文档
7. 提交 pull request

### 🛠️ 开发环境设置

1. **前置要求**
   - Java 21 或更高版本
   - Maven 3.6 或更高版本
   - Docker 和 Docker Compose（用于运行中间件）

2. **克隆仓库**
   ```bash
   git clone https://github.com/lihongjie0209/dht-spider-java.git
   cd dht-spider-java
   ```

3. **启动中间件**
   ```bash
   docker-compose up -d redpanda redis postgres console
   ```

4. **构建项目**
   ```bash
   mvn clean package
   ```

5. **运行测试**
   ```bash
   mvn test
   ```

### 🔄 Pull Request 流程

1. 如适用，更新 README.md 以包含更改的详细信息
2. 更新任何已更改的 API 或配置的文档
3. PR 在至少一位维护者签署后将被合并

### 📝 编码规范

- 遵循 Java 命名约定
- 编写清晰、自文档化的代码
- 为复杂逻辑添加注释
- 保持方法专注和简洁
- 为新功能编写单元测试
- 确保代码通过所有现有测试
- 使用有意义的提交消息

#### 提交消息格式

```
<类型>: <主题>

<正文>

<页脚>
```

类型：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更改
- `style`: 代码样式更改（格式化等）
- `refactor`: 代码重构
- `test`: 添加或更新测试
- `chore`: 维护任务

示例：
```
feat: 添加对 IPv6 DHT 节点的支持

- 实现 IPv6 地址解析
- 添加 IPv6 绑定配置
- 更新测试以覆盖 IPv6 场景

Closes #123
```

### 🧪 测试指南

- 为所有新功能编写单元测试
- 确保集成测试通过
- 测试边缘情况和错误条件
- 保持或提高代码覆盖率

### 📚 文档

- 为任何更改更新相关文档
- 为公共 API 包含文档字符串
- 为重大更改更新 README.md
- 为复杂逻辑添加代码注释

### ❓ 有问题？

如有任何问题或疑虑，欢迎开启 issue。
