/**
 * 投影仪快传 - 前端核心交互逻辑
 * 架构设计模式：
 * 1. 策略模式 (Strategy Pattern): 统一处理点击选择与拖拽文件夹树的文件提取
 * 2. 状态模式 (State Pattern): 独立封装上传任务的生命周期状态流转
 * 3. 观察者模式 (Observer Pattern): 数据状态驱动 UI 列表与按钮自动更新
 * 4. 模板方法模式 (Template Method): 封装标准化的 XHR 表单提交与错误处理流程
 */

// ==================== 1. 全局可定制配置 ====================
const CONFIG = {
  MAX_FILES: 10,                // 单次最多允许传输的文件数量（可随时修改）
  MAX_FILE_SIZE_MB: 4096,       // 单文件大小上限（默认 4GB，可按需调整）
  UPLOAD_URL: '/'               // 后端接收地址
};

// ==================== 2. 工具类 ====================
const FileUtils = {
  formatSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  },
  escapeHtml(str) {
    const div = document.createElement('div');
    div.innerText = str;
    return div.innerHTML;
  }
};

// ==================== 3. 全局 Toast 通知单例 ====================
class ToastNotifier {
  constructor(elementId) {
    this.el = document.getElementById(elementId);
    this.timer = null;
  }

  show(msg, duration = 3000) {
    if (!this.el) return;
    this.el.innerText = msg;
    this.el.classList.add('show');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.el.classList.remove('show');
    }, duration);
  }
}
const toast = new ToastNotifier('toast');

// ==================== 4. 状态模式：上传任务状态定义 ====================
class TaskState {
  constructor(code, defaultLabel) {
    this.code = code;           // pending | uploading | completed | failed
    this.defaultLabel = defaultLabel;
  }
}

const TASK_STATES = Object.freeze({
  PENDING: new TaskState('pending', '等待上传'),
  UPLOADING: new TaskState('uploading', '0%'),
  COMPLETED: new TaskState('completed', '传输成功'),
  FAILED: new TaskState('failed', '传输失败')
});

class UploadTask {
  constructor(file, relativePath = '') {
    this.id = Date.now() + Math.random().toString(36).slice(2, 7);
    this.file = file;
    this.relativePath = relativePath;
    this.state = TASK_STATES.PENDING;
    this.progress = 0;
    this.statusText = TASK_STATES.PENDING.defaultLabel;
  }

  transitionTo(newState, customText = null, progress = null) {
    this.state = newState;
    this.statusText = customText !== null ? customText : newState.defaultLabel;
    if (progress !== null) {
      this.progress = progress;
    }
  }

  get displayPath() {
    return this.relativePath ? `${this.relativePath}/${this.file.name}` : this.file.name;
  }
}

// ==================== 5. 策略模式：多来源文件提取策略 ====================
class FileExtractStrategy {
  async extract(source) {
    throw new Error('子类必须实现 extract 方法');
  }
}

/** 策略 A：通过 <input type="file"> 选择文件或文件夹 */
class InputExtractStrategy extends FileExtractStrategy {
  async extract(fileList) {
    const results = [];
    for (let i = 0; i < fileList.length; i++) {
      const file = fileList[i];
      let relPath = '';
      if (file.webkitRelativePath) {
        const parts = file.webkitRelativePath.split('/');
        parts.pop(); // 移除文件名，仅保留父级相对目录
        relPath = parts.join('/');
      }
      results.push({ file, relativePath: relPath });
    }
    return results;
  }
}

/** 策略 B：通过拖拽（Drag & Drop）递归遍历文件与文件夹树 */
class DropExtractStrategy extends FileExtractStrategy {
  async extract(dataTransferItems) {
    const results = [];

    const readDirectoryEntries = (reader) => new Promise((resolve) => {
      const allEntries = [];
      const readBatch = () => {
        reader.readEntries((entries) => {
          if (entries.length === 0) {
            resolve(allEntries);
          } else {
            allEntries.push(...entries);
            readBatch(); // 突破单次 readEntries 最多返回 100 条的浏览器限制
          }
        });
      };
      readBatch();
    });

    const traverseEntry = (entry, currentPath) => new Promise((resolve) => {
      if (entry.isFile) {
        entry.file((file) => {
          results.push({ file, relativePath: currentPath });
          resolve();
        }, () => resolve());
      } else if (entry.isDirectory) {
        const dirReader = entry.createReader();
        readDirectoryEntries(dirReader).then(async (entries) => {
          const nextPath = currentPath ? `${currentPath}/${entry.name}` : entry.name;
          for (const child of entries) {
            await traverseEntry(child, nextPath);
          }
          resolve();
        });
      } else {
        resolve();
      }
    });

    for (let i = 0; i < dataTransferItems.length; i++) {
      const item = dataTransferItems[i];
      if (item.kind === 'file') {
        const entry = item.webkitGetAsEntry ? item.webkitGetAsEntry() : null;
        if (entry) {
          await traverseEntry(entry, '');
        } else {
          const file = item.getAsFile();
          if (file) results.push({ file, relativePath: '' });
        }
      }
    }
    return results;
  }
}

// ==================== 6. 模板方法模式：XHR 传输执行器 ====================
class XhrRequestExecutor {
  send({ formData, onProgress }) {
    return new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', CONFIG.UPLOAD_URL, true);

      if (onProgress && xhr.upload) {
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            onProgress(percent);
          }
        };
      }

      xhr.onload = () => {
        if (xhr.status === 200) {
          try {
            const res = JSON.parse(xhr.responseText);
            resolve({ ok: !!res.success, message: res.message || '操作成功' });
          } catch (_) {
            resolve({ ok: false, message: '响应解析异常' });
          }
        } else {
          resolve({ ok: false, message: `服务器错误 (${xhr.status})` });
        }
      };

      xhr.onerror = () => resolve({ ok: false, message: '网络连接中断' });
      xhr.ontimeout = () => resolve({ ok: false, message: '传输超时' });

      xhr.send(formData);
    });
  }
}

// ==================== 7. 观察者模式：任务队列状态中心 ====================
class UploadStore {
  constructor(maxFiles, maxFileSizeMB) {
    this.maxFiles = maxFiles;
    this.maxFileSizeBytes = maxFileSizeMB * 1024 * 1024;
    this.tasks = [];
    this.isUploading = false;
    this.observers = [];
    this.executor = new XhrRequestExecutor();
  }

  subscribe(observerFn) {
    this.observers.push(observerFn);
  }

  notify() {
    this.observers.forEach((fn) => fn(this.tasks, this.isUploading));
  }

  addItems(extractedItems) {
    let countOverflow = false;
    let sizeOverflow = false;

    for (const item of extractedItems) {
      if (this.tasks.length >= this.maxFiles) {
        countOverflow = true;
        break;
      }
      if (item.file.size > this.maxFileSizeBytes) {
        sizeOverflow = true;
        continue;
      }
      this.tasks.push(new UploadTask(item.file, item.relativePath));
    }

    if (countOverflow) {
      toast.show(`单次最多支持 ${this.maxFiles} 个文件，超出部分已忽略`);
    } else if (sizeOverflow) {
      toast.show(`部分文件超过 ${CONFIG.MAX_FILE_SIZE_MB}MB 上限已跳过`);
    }

    this.notify();
  }

  clear() {
    if (this.isUploading) {
      toast.show('正在传输中，无法清空列表');
      return;
    }
    this.tasks = [];
    this.notify();
  }

  async startQueue(targetFolder, newSubFolder) {
    if (this.isUploading) return;
    this.isUploading = true;
    this.notify();

    let successCount = 0;

    // 逐个队列串行传输，防止并发大文件导致 Android 端内存溢出
    for (const task of this.tasks) {
      if (task.state !== TASK_STATES.PENDING) continue;

      task.transitionTo(TASK_STATES.UPLOADING, '0%', 0);
      this.notify();

      const formData = new FormData();
      formData.append('file', task.file, task.file.name);
      formData.append('folder', targetFolder);
      formData.append('new_folder', newSubFolder);
      formData.append('relative_path', task.relativePath);

      const result = await this.executor.send({
        formData,
        onProgress: (percent) => {
          task.transitionTo(TASK_STATES.UPLOADING, `${percent}%`, percent);
          this.notify();
        }
      });

      if (result.ok) {
        task.transitionTo(TASK_STATES.COMPLETED, '传输成功', 100);
        successCount++;
      } else {
        task.transitionTo(TASK_STATES.FAILED, result.message);
      }
      this.notify();
    }

    this.isUploading = false;
    this.notify();

    if (successCount > 0) {
      toast.show(`传输完成！成功发送 ${successCount} 个文件`);
    }
  }
}

// ==================== 8. 应用初始化与 DOM 事件绑定 ====================
document.addEventListener('DOMContentLoaded', () => {
  const store = new UploadStore(CONFIG.MAX_FILES, CONFIG.MAX_FILE_SIZE_MB);
  const inputStrategy = new InputExtractStrategy();
  const dropStrategy = new DropExtractStrategy();
  const textExecutor = new XhrRequestExecutor();
  // 初始化界面最大数量显示
  const maxCountLabel = document.getElementById('maxCountLabel');
  if (maxCountLabel) maxCountLabel.innerText = CONFIG.MAX_FILES;
  // 订阅任务列表变更，自动渲染视图
  const fileListEl = document.getElementById('fileList');
  const startUploadBtn = document.getElementById('startUploadBtn');
  store.subscribe((tasks, isUploading) => {
    fileListEl.innerHTML = tasks.map((task) => `
        <li class="file-item">
            <div class="file-header">
                <span class="file-name">${FileUtils.escapeHtml(task.displayPath)} <small style="color:#64748b">(${FileUtils.formatSize(task.file.size)})</small></span>
                <span class="file-status status-${task.state.code}">${FileUtils.escapeHtml(task.statusText)}</span>
            </div>
            <div class="progress-bar">
                <div class="progress-fill ${task.state.code}" style="width: ${task.progress}%"></div>
            </div>
        </li>
    `).join('');
    const pendingCount = tasks.filter((t) => t.state === TASK_STATES.PENDING).length;
    startUploadBtn.innerText = isUploading ? '正在传输中...' : 开始上传(${ pendingCount });
    startUploadBtn.disabled = isUploading || pendingCount === 0;
  });
  // 1. 文字发送逻辑
  const sendTextBtn = document.getElementById('sendTextBtn');
  const textInput = document.getElementById('textInput');
  sendTextBtn.addEventListener('click', async () => {
    const text = textInput.value.trim();
    if (!text) {
      toast.show('请输入要发送的文字');
      return;
    }
    sendTextBtn.disabled = true;
    const formData = new FormData();
    formData.append('text', text);
    const result = await textExecutor.send({ formData });
    sendTextBtn.disabled = false;
    toast.show(result.message);
    if (result.ok) textInput.value = '';
  });
  // 2. 点击按钮选择文件 / 文件夹
  const fileInput = document.getElementById('fileInput');
  const folderInput = document.getElementById('folderInput');
  document.getElementById('btnPickFiles').addEventListener('click', () => fileInput.click());
  document.getElementById('btnPickFolder').addEventListener('click', () => folderInput.click());
  const handleInputChange = async (e) => {
    if (e.target.files && e.target.files.length > 0) {
      const items = await inputStrategy.extract(e.target.files);
      store.addItems(items);
      e.target.value = '';
    }
  };
  fileInput.addEventListener('change', handleInputChange);
  folderInput.addEventListener('change', handleInputChange);
  // 3. 拖拽文件 / 文件夹区域交互
  const dropZone = document.getElementById('dropZone');
  dropZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZone.classList.add('dragover');
  });
  dropZone.addEventListener('dragleave', () => {
    dropZone.classList.remove('dragover');
  });
  dropZone.addEventListener('drop', async (e) => {
    e.preventDefault();
    dropZone.classList.remove('dragover');
    if (e.dataTransfer && e.dataTransfer.items) {
      const items = await dropStrategy.extract(e.dataTransfer.items);
      store.addItems(items);
    }
  });
  // 4. 开始上传与清空队列
  startUploadBtn.addEventListener('click', () => {
    const folder = document.getElementById('folderSelect').value;
    const newFolder = document.getElementById('newFolderInput').value.trim();
    store.startQueue(folder, newFolder);
  });
  document.getElementById('clearListBtn').addEventListener('click', () => store.clear());
});