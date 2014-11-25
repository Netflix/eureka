/**
 * Builds a simple collapsible tree of ul->li elements.
 * It needs bootstrap 3+ with glyphicon support
 * @param opts
 * @returns {{init: init}}
 * @constructor
 */
function SimpleTreeView(opts) {
    opts = opts || {};
    var cont = opts.contId || 'tree-view';
    var treeData = opts.data || {};

    var glyphClasses = {
        PLUS : 'glyphicon-plus',
        MINUS: 'glyphicon-minus',
        MAIN : 'glyphicon'
    };

    var listClasses = {
        HDR: 'li-hdr'
    };

    var treeNodeState = {
        OPEN : 'expanded',
        CLOSE: 'collapsed',
        NONE : 'unavailable'
    };

    function init() {
        if (treeData) {
            var treeMarkup = MarkupBuilder(treeData).build();
            $('#' + cont).html(treeMarkup);
        }

        $('#' + cont + " .li-hdr").on('click', function (evt) {
            evt.preventDefault();
            var targetElm = $(evt.target);
            toggleState(targetElm);
        });
    }

    function MarkupBuilder(treeNodesMap) {
        var markup = [];

        function buildMarkup(nodeMap) {
            markup.push('<ul>');

            Object.keys(nodeMap).forEach(function (node) {
                var childNodes = nodeMap[node];

                var numChildNodes = childNodes.length;
                markup.push('<li>');
                markup.push('<span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>' + node + ' - ( ' + numChildNodes + ' )</span>');

                markup.push('<ul>');

                childNodes.forEach(function (cNode) {
                    if (typeof cNode !== 'object') {
                        markup.push('<li>' + cNode + '</li>');
                    } else {
                        // build next level
                        buildMarkup(cNode);
                    }
                });
                markup.push('</ul>');
                markup.push('</li>');
            });
            markup.push('</ul>');
        }

        return {
            build: function () {
                buildMarkup(treeNodesMap);
                return markup.join('');
            }
        }
    }


    function toggleState(clkTarget) {
        var glyphElm;
        if ($(clkTarget).hasClass(listClasses.HDR)) {
            // label clicked
            glyphElm = $(clkTarget).children('.' + glyphClasses.MAIN);
        } else if ($(clkTarget).hasClass(glyphClasses.MAIN)) {
            // glyph clicked
            glyphElm = $(clkTarget);
        }

        toggleNode(glyphElm);
    }

    function findCurrentState(targetElm) {
        if ($(targetElm).hasClass(glyphClasses.MAIN)) {
            if ($(targetElm).hasClass(glyphClasses.PLUS)) {
                return treeNodeState.CLOSE;
            } else {
                return treeNodeState.OPEN;
            }
        }
        return treeNodeState.NONE;
    }

    function toggleNode(glyphElm) {
        var curState = findCurrentState(glyphElm);

        if (curState == treeNodeState.OPEN) {
            // collapse
            glyphElm.parent().siblings('ul').hide();
        } else {
            glyphElm.parent().siblings('ul').show();
        }
        toggleIcon(glyphElm);

        function toggleIcon(spanElm) {
            var spElm = $(spanElm);
            if (curState == treeNodeState.OPEN) {
                toggleElmClass(spElm, glyphClasses.PLUS, glyphClasses.MINUS);
            } else {
                toggleElmClass(spElm, glyphClasses.MINUS, glyphClasses.PLUS);
            }
        }

        function toggleElmClass(elm, clsToAdd, clsToRemove) {
            elm.removeClass(clsToRemove);
            elm.addClass(clsToAdd);
        }

    }

    return {
        init         : init,
        markupBuilder: MarkupBuilder
    }
}

if (typeof exports !== 'undefined') {
    exports.SimpleTreeView = SimpleTreeView;
}

