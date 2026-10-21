const pageSize = 10;
let currentPage = 0;
let totalPages = 0;

const rows = document.querySelector('#event-rows');
const emptyState = document.querySelector('#empty-state');
const notice = document.querySelector('#notice');

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function formatTime(value) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

async function loadEvents() {
  const params = new URLSearchParams({ page: String(currentPage), size: String(pageSize) });
  const actorId = document.querySelector('#actor-filter').value.trim();
  const outcome = document.querySelector('#outcome-filter').value;
  if (actorId) params.set('actorId', actorId);
  if (outcome) params.set('outcome', outcome);

  try {
    const response = await fetch(`/api/audit-events?${params}`);
    if (!response.ok) throw new Error('加载审计记录失败');
    const result = await response.json();
    totalPages = result.totalPages;
    rows.innerHTML = result.content.map((event) => `
      <tr>
        <td>${escapeHtml(formatTime(event.occurredAt))}</td>
        <td>${event.actorName ? `${escapeHtml(event.actorName)}<small class="actor-id">${escapeHtml(event.actorId)}</small>` : escapeHtml(event.actorId)}</td>
        <td><span class="mono">${escapeHtml(event.action)}</span></td>
        <td><span class="badge ${event.outcome === 'SUCCESS' ? 'success' : 'failure'}">${event.outcome === 'SUCCESS' ? '成功' : '失败'}</span></td>
        <td>${event.durationMs} ms</td>
        <td>${escapeHtml(event.httpMethod || '—')} ${escapeHtml(event.url || '')}</td>
        <td>${escapeHtml(event.httpStatus ?? '—')}</td>
        <td class="user-agent" title="${escapeHtml(event.errorMessage || '')}">${escapeHtml(event.errorType || '—')}${event.errorMessage ? `: ${escapeHtml(event.errorMessage)}` : ''}</td>
        <td class="mono">${escapeHtml(event.clientIp || '—')}</td>
        <td class="user-agent" title="${escapeHtml(event.userAgent || '')}">${escapeHtml(event.userAgent || '—')}</td>
      </tr>`).join('');
    emptyState.hidden = result.content.length > 0;
    document.querySelector('#page-summary').textContent = `共 ${result.totalElements} 条 · 第 ${result.totalPages === 0 ? 0 : currentPage + 1} / ${result.totalPages} 页`;
    document.querySelector('#previous-button').disabled = currentPage <= 0;
    document.querySelector('#next-button').disabled = currentPage + 1 >= totalPages;
  } catch (error) {
    rows.innerHTML = '';
    emptyState.hidden = false;
    emptyState.textContent = error.message;
  }
}

async function waitForAuditEvent(kind, since) {
  const action = kind === 'success' ? 'DemoController.succeed' : 'DemoController.fail';
  for (let attempt = 0; attempt < 20; attempt++) {
    try {
      const response = await fetch('/api/audit-events?page=0&size=200');
      if (response.ok) {
        const result = await response.json();
        if (result.content.some((event) => event.action === action && Date.parse(event.occurredAt) >= since)) return true;
      }
    } catch {
      // Keep checking until the audit event is stored or the wait expires.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return false;
}

async function runOperation(kind) {
  const actor = document.querySelector('#actor').value.trim() || 'demo-user';
  const actorName = document.querySelector('#actor-name').value.trim();
  const targetId = document.querySelector('#target').value.trim() || 'record-1001';
  const startedAt = Date.now();
  notice.classList.remove('error');
  notice.textContent = '正在执行…';
  try {
    const response = await fetch(`/api/demo/${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Demo-Actor': actor, 'X-Demo-Actor-Name': actorName },
      body: JSON.stringify({ targetId })
    });
    const result = await response.json();
    notice.textContent = result.message;
    notice.classList.toggle('error', !response.ok);
    if (!await waitForAuditEvent(kind, startedAt)) {
      notice.textContent += '（审计记录仍在异步处理中，可稍后刷新）';
    }
  } catch {
    notice.textContent = '请求失败，请检查示例服务是否运行。';
    notice.classList.add('error');
  }
  currentPage = 0;
  await loadEvents();
}

document.querySelector('#success-button').addEventListener('click', () => runOperation('success'));
document.querySelector('#failure-button').addEventListener('click', () => runOperation('failure'));
document.querySelector('#refresh-button').addEventListener('click', loadEvents);
document.querySelector('#filter-button').addEventListener('click', () => { currentPage = 0; loadEvents(); });
document.querySelector('#previous-button').addEventListener('click', () => { currentPage--; loadEvents(); });
document.querySelector('#next-button').addEventListener('click', () => { currentPage++; loadEvents(); });

loadEvents();
