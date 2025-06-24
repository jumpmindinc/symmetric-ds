/*
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
ace.define("ace/ext/spellcheck",["require","exports","module","ace/lib/event","ace/editor","ace/config"],function(e,t,n){"use strict";var r=e("../lib/event");t.contextMenuHandler=function(e){var t=e.target,n=t.textInput.getElement();if(!t.selection.isEmpty())return;var i=t.getCursorPosition(),s=t.session.getWordRange(i.row,i.column),o=t.session.getTextRange(s);t.session.tokenRe.lastIndex=0;if(!t.session.tokenRe.test(o))return;var u="",a=o+" "+u;n.value=a,n.setSelectionRange(o.length,o.length+1),n.setSelectionRange(0,0),n.setSelectionRange(0,o.length);var f=!1;r.addListener(n,"keydown",function l(){r.removeListener(n,"keydown",l),f=!0}),t.textInput.setInputHandler(function(e){console.log(e,a,n.selectionStart,n.selectionEnd);if(e==a)return"";if(e.lastIndexOf(a,0)===0)return e.slice(a.length);if(e.substr(n.selectionEnd)==a)return e.slice(0,-a.length);if(e.slice(-2)==u){var r=e.slice(0,-2);if(r.slice(-1)==" ")return f?r.substring(0,n.selectionEnd):(r=r.slice(0,-1),t.session.replace(s,r),"")}return e})};var i=e("../editor").Editor;e("../config").defineOptions(i.prototype,"editor",{spellcheck:{set:function(e){var n=this.textInput.getElement();n.spellcheck=!!e,e?this.on("nativecontextmenu",t.contextMenuHandler):this.removeListener("nativecontextmenu",t.contextMenuHandler)},value:!0}})});
                (function() {
                    ace.require(["ace/ext/spellcheck"], function() {});
                })();
            