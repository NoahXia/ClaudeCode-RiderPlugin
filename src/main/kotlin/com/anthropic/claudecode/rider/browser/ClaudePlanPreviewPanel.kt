package com.anthropic.claudecode.rider.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * Renders Claude's plan in a dedicated "Claude Plan" tool window using JCEF.
 * Handles the open_markdown_preview / close_plan_preview / remove_plan_comment
 * RPC lifecycle and bridges user comments back to the React webview via
 * ClaudeBrowserManager.sendPlanComment().
 */
class ClaudePlanPreviewPanel(
    private val project: Project,
    parentDisposable: Disposable,
    private val onComment: (channelId: String, comment: JsonObject) -> Unit
) : Disposable {

    companion object {
        const val TOOL_WINDOW_ID = "Claude Plan"
        private val log = Logger.getInstance(ClaudePlanPreviewPanel::class.java)
    }

    private val browser = JBCefBrowserBuilder().setOffScreenRendering(false).build()
    private val commentQuery: JBCefJSQuery = JBCefJSQuery.create(browser)
    var currentChannelId: String = ""
        private set

    init {
        Disposer.register(parentDisposable, this)
        commentQuery.addHandler { msg ->
            runCatching {
                val obj = Json.parseToJsonElement(msg).jsonObject
                when (obj["action"]?.jsonPrimitive?.contentOrNull) {
                    "add" -> {
                        val comment = obj["comment"]?.jsonObject ?: return@addHandler null
                        onComment(currentChannelId, comment)
                    }
                    // "remove" is already handled locally in JS;
                    // React-side removal arrives via remove_plan_comment RPC from ClaudeMessageRouter.
                }
            }.onFailure { log.warn("Failed to parse plan comment from JS: ${it.message}") }
            null
        }
    }

    fun show(channelId: String, content: String, title: String, enableComments: Boolean) {
        currentChannelId = channelId
        val isDark = !JBColor.isBright()
        val html = generateHtml(content, title, enableComments, isDark)
        ApplicationManager.getApplication().invokeLater {
            browser.loadHTML(html)
            val twm = ToolWindowManager.getInstance(project)
            val tw = twm.getToolWindow(TOOL_WINDOW_ID)
                ?: twm.registerToolWindow(
                    RegisterToolWindowTask(id = TOOL_WINDOW_ID, anchor = ToolWindowAnchor.RIGHT)
                )
            tw.contentManager.let { cm ->
                cm.removeAllContents(true)
                cm.addContent(cm.factory.createContent(browser.component, title, false))
            }
            tw.show()
        }
    }

    fun close() {
        ApplicationManager.getApplication().invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.hide()
        }
    }

    /** Called by ClaudeMessageRouter when React sends remove_plan_comment. */
    fun removeComment(commentId: String) {
        val b64 = Base64.getEncoder().encodeToString(commentId.toByteArray(Charsets.UTF_8))
        browser.cefBrowser.executeJavaScript(
            "window.__removeComment && window.__removeComment(atob('$b64'));", "", 0
        )
    }

    // ── HTML generation ───────────────────────────────────────────────────────

    private fun generateHtml(
        content: String,
        title: String,
        enableComments: Boolean,
        isDark: Boolean
    ): String {
        val contentB64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val jsCallback = commentQuery.inject("JSON.stringify(commentData)")

        // Theme palette
        val bg          = if (isDark) "#1e1e1e" else "#ffffff"
        val fg          = if (isDark) "#cccccc" else "#333333"
        val border      = if (isDark) "#3e3e3e" else "#e0e0e0"
        val headerBg    = if (isDark) "#252526" else "#f3f3f3"
        val codeBg      = if (isDark) "#2d2d2d" else "#f5f5f5"
        val accentFg    = if (isDark) "#4fc1ff" else "#1d4ed8"
        val accentBg    = if (isDark) "#094771" else "#dbeafe"
        val muted       = if (isDark) "#888888" else "#666666"
        val commentBg   = if (isDark) "#2a2510" else "#fffbeb"
        val highlightBg = if (isDark) "#3a3a1a" else "#fef9c3"
        val codeColor   = if (isDark) "#ce9178" else "#d73a49"
        val h3Color     = if (isDark) "#9cdcfe" else "#1e40af"

        return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:$bg;color:$fg;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-size:13px;line-height:1.6;height:100vh;display:flex;flex-direction:column}
#header{background:$headerBg;border-bottom:1px solid $border;padding:10px 16px;position:sticky;top:0;z-index:100;flex-shrink:0}
.status-badge{font-size:11px;font-weight:600;color:$accentFg;background:$accentBg;display:inline-block;padding:2px 8px;border-radius:10px;margin-bottom:2px}
.subtitle{font-size:11px;color:$muted}
#layout{display:flex;flex:1;overflow:hidden}
#plan-content{flex:1;padding:20px 24px;overflow-y:auto}
#plan-content h1{font-size:1.5em;font-weight:700;margin:0 0 16px;padding-bottom:8px;border-bottom:1px solid $border}
#plan-content h2{font-size:1.2em;font-weight:600;margin:20px 0 10px}
#plan-content h3{font-size:1.05em;font-weight:600;margin:16px 0 8px;color:$h3Color}
#plan-content h4{font-size:.95em;font-weight:600;margin:12px 0 6px}
#plan-content p{margin:0 0 10px}
#plan-content ul,#plan-content ol{margin:6px 0 10px 22px}
#plan-content li{margin:3px 0}
#plan-content code{background:$codeBg;padding:1px 5px;border-radius:3px;font-family:monospace;font-size:12px;color:$codeColor}
#plan-content pre{background:$codeBg;border:1px solid $border;border-radius:6px;padding:12px;margin:10px 0;overflow-x:auto}
#plan-content pre code{background:none;padding:0;color:$fg}
#plan-content hr{border:none;border-top:1px solid $border;margin:16px 0}
#plan-content strong{font-weight:600}
#plan-content em{font-style:italic}
.highlight{background:$highlightBg;border-radius:2px}
#comments-sidebar{width:230px;min-width:230px;border-left:1px solid $border;padding:10px 8px;overflow-y:auto;background:$bg}
#comments-sidebar .sidebar-title{font-size:11px;font-weight:600;color:$muted;text-transform:uppercase;letter-spacing:.5px;margin-bottom:8px}
.sidebar-empty{color:$muted;font-size:11px;text-align:center;margin-top:16px}
.comment-card{background:$commentBg;border-left:3px solid #f59e0b;border-radius:4px;padding:8px 10px;margin-bottom:8px;font-size:12px}
.comment-sel{font-style:italic;color:$muted;font-size:11px;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.comment-text{color:$fg;word-break:break-word;margin-bottom:4px}
.comment-remove{color:$muted;font-size:10px;cursor:pointer;background:none;border:none;padding:0;float:right}
.comment-remove:hover{color:#cc4444}
#comment-btn{position:fixed;display:none;background:$accentFg;color:#fff;border:none;border-radius:4px;padding:4px 10px;font-size:11px;cursor:pointer;z-index:200;box-shadow:0 2px 6px rgba(0,0,0,.25)}
#comment-btn:hover{opacity:.9}
#comment-box{display:none;position:fixed;background:$bg;border:1px solid #f59e0b;border-radius:6px;padding:10px;z-index:300;box-shadow:0 4px 14px rgba(0,0,0,.3);width:250px}
#comment-box textarea{width:100%;min-height:60px;background:$codeBg;color:$fg;border:1px solid $border;border-radius:4px;padding:6px;font-size:12px;resize:vertical;outline:none;font-family:inherit}
.btn-row{display:flex;gap:6px;margin-top:6px;justify-content:flex-end}
.btn-row button{padding:4px 10px;font-size:11px;border-radius:4px;border:none;cursor:pointer}
.btn-submit{background:$accentFg;color:#fff}
.btn-cancel{background:$codeBg;color:$fg;border:1px solid $border !important}
</style>
</head>
<body>
<div id="header">
  <div class="status-badge">Ready for review</div>
  <div class="subtitle">${if (enableComments) "Select text to add comments on the plan" else "Plan is ready"}</div>
</div>
<div id="layout">
  <div id="plan-content"></div>
  ${if (enableComments) """
  <div id="comments-sidebar">
    <div class="sidebar-title">Comments</div>
    <div id="comments-list"><div class="sidebar-empty">No comments yet</div></div>
  </div>""" else ""}
</div>
${if (enableComments) """
<button id="comment-btn">+ Comment</button>
<div id="comment-box">
  <textarea id="comment-ta" placeholder="Add a comment..."></textarea>
  <div class="btn-row">
    <button class="btn-cancel" onclick="cancelComment()">Cancel</button>
    <button class="btn-submit" onclick="submitComment()">Add Comment</button>
  </div>
</div>""" else ""}
<script>
(function(){
  // ── Decode base64 content ─────────────────────────────────────────────────
  function decodeB64(b64){
    return new TextDecoder('utf-8').decode(Uint8Array.from(atob(b64),function(c){return c.charCodeAt(0)}));
  }
  var planContent = decodeB64('$contentB64');

  // ── Markdown renderer ─────────────────────────────────────────────────────
  function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
  function renderMd(md){
    var blocks=[];
    // fenced code blocks
    md=md.replace(/```([\w]*)\n?([\s\S]*?)```/g,function(_,lang,code){
      blocks.push('<pre><code>'+esc(code.trim())+'</code></pre>');
      return '\x00B'+(blocks.length-1)+'\x00';
    });
    // inline code
    md=md.replace(/`([^`\n]+)`/g,function(_,c){return '<code>'+esc(c)+'</code>';});
    // headers
    md=md.replace(/^#### (.+)${'$'}/gm,'<h4>${'$'}1</h4>');
    md=md.replace(/^### (.+)${'$'}/gm,'<h3>${'$'}1</h3>');
    md=md.replace(/^## (.+)${'$'}/gm,'<h2>${'$'}1</h2>');
    md=md.replace(/^# (.+)${'$'}/gm,'<h1>${'$'}1</h1>');
    // bold / italic
    md=md.replace(/\*\*\*(.+?)\*\*\*/g,'<strong><em>${'$'}1</em></strong>');
    md=md.replace(/\*\*(.+?)\*\*/g,'<strong>${'$'}1</strong>');
    md=md.replace(/\*(.+?)\*/g,'<em>${'$'}1</em>');
    // hr
    md=md.replace(/^---+${'$'}/gm,'<hr>');
    // lists (flat — good enough for plans)
    md=md.replace(/^[ \t]*[-*+] (.+)${'$'}/gm,'<li>${'$'}1</li>');
    md=md.replace(/^[ \t]*\d+\. (.+)${'$'}/gm,'<li>${'$'}1</li>');
    md=md.replace(/(<li>[\s\S]*?<\/li>\n?)+/g,'<ul>${'$'}&</ul>');
    // paragraphs
    var parts=md.split(/\n{2,}/);
    md=parts.map(function(p){
      p=p.trim();if(!p)return '';
      if(/^<[hup]|^<ul|^<ol|^<hr|^\x00B/.test(p))return p;
      return '<p>'+p.replace(/\n/g,'<br>')+'</p>';
    }).join('\n');
    // restore code blocks
    blocks.forEach(function(b,i){md=md.replace('\x00B'+i+'\x00',b);});
    return md;
  }
  document.getElementById('plan-content').innerHTML=renderMd(planContent);

  if(!${enableComments})return;

  // ── Comment system ────────────────────────────────────────────────────────
  var comments=[];
  var pendingText='';
  var commentBtn=document.getElementById('comment-btn');
  var commentBox=document.getElementById('comment-box');
  var commentsList=document.getElementById('comments-list');

  function renderComments(){
    if(!comments.length){commentsList.innerHTML='<div class="sidebar-empty">No comments yet</div>';return;}
    commentsList.innerHTML=comments.map(function(c){
      return '<div class="comment-card" id="cc-'+c.id+'">'
        +'<button class="comment-remove" onclick="removeLocal(\''+c.id+'\')">✕</button>'
        +'<div class="comment-sel">"'+esc((c.selectedText||'').substring(0,50))+(c.selectedText&&c.selectedText.length>50?'…':'')+'"</div>'
        +'<div class="comment-text">'+esc(c.comment)+'</div>'
        +'</div>';
    }).join('');
  }

  window.removeLocal=function(id){
    comments=comments.filter(function(c){return c.id!==id;});
    renderComments();
  };

  // Called by Rider when React sends remove_plan_comment
  window.__removeComment=function(id){
    comments=comments.filter(function(c){return c.id!==id;});
    renderComments();
  };

  document.addEventListener('mouseup',function(e){
    if(commentBox.contains(e.target)||commentBtn.contains(e.target))return;
    var sel=window.getSelection();
    var txt=sel?sel.toString().trim():'';
    if(txt){
      pendingText=txt;
      commentBtn.style.display='block';
      var r=sel.getRangeAt(0).getBoundingClientRect();
      commentBtn.style.left=Math.min(r.left,window.innerWidth-120)+'px';
      commentBtn.style.top=(r.top-36+window.scrollY)+'px';
    }else{
      commentBtn.style.display='none';
    }
  });

  commentBtn.onclick=function(){
    commentBtn.style.display='none';
    var l=parseInt(commentBtn.style.left)||100;
    var t=parseInt(commentBtn.style.top)||100;
    commentBox.style.left=Math.min(l,window.innerWidth-260)+'px';
    commentBox.style.top=(t+42)+'px';
    commentBox.style.display='block';
    document.getElementById('comment-ta').value='';
    document.getElementById('comment-ta').focus();
  };

  document.addEventListener('mousedown',function(e){
    if(!commentBox.contains(e.target)&&!commentBtn.contains(e.target)){
      commentBox.style.display='none';
      commentBtn.style.display='none';
    }
  });

  window.cancelComment=function(){
    commentBox.style.display='none';
    pendingText='';
  };

  window.submitComment=function(){
    var text=document.getElementById('comment-ta').value.trim();
    if(!text)return;
    var id=Math.random().toString(36).substr(2,9);
    var newComment={id:id,comment:text,selectedText:pendingText};
    comments.push(newComment);
    renderComments();
    commentBox.style.display='none';
    pendingText='';
    // Send to Rider → React via JBCefJSQuery
    var commentData={action:'add',comment:newComment};
    $jsCallback;
  };
})();
</script>
</body>
</html>"""
    }

    override fun dispose() {
        Disposer.dispose(commentQuery)
        Disposer.dispose(browser)
    }
}
