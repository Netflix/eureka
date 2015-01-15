describe('simple tree view', function () {
    describe('verify markup', function () {

        var ExpectedMarkups = {
            NestedNodeLevelMarkup: '<ul><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>UP - ( 3 )</span><ul><li>instance-1</li><li>instance-2</li><li>instance-3</li></ul></li><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>DOWN - ( 3 )</span><ul><li>instance-1</li><li>instance-2</li><li>instance-3</li></ul></li><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>STARTING - ( 4 )</span><ul><li>instance-1</li><li>instance-2</li><ul><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>STUCK - ( 2 )</span><ul><li>instance-33</li><li>instance-3343</li></ul></li></ul><li>instance-3</li></ul></li></ul>',
            MultiNodeLevelsMarkup: '<ul><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>UP - ( 3 )</span><ul><li>instance-1</li><li>instance-2</li><li>instance-3</li></ul></li><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>DOWN - ( 3 )</span><ul><li>instance-11</li><li>instance-22</li><li>instance-76</li></ul></li></ul>',
            SingleNodeLevelMarkup: '<ul><li><span class="li-hdr"><span class="glyphicon glyphicon-minus"></span>UP - ( 3 )</span><ul><li>instance-1</li><li>instance-2</li><li>instance-3</li></ul></li></ul>'
        };

        it('single top level node', function () {
            var testData = {
                "UP": ["instance-1", "instance-2", "instance-3"]
            };
            var markupBuilder = SimpleTreeView().markupBuilder(testData);
            expect(markupBuilder.build()).toEqual(ExpectedMarkups.SingleNodeLevelMarkup);
        });

        it('multiple top level nodes', function () {
            var testData = {
                "UP"  : ["instance-1", "instance-2", "instance-3"],
                "DOWN": ["instance-11", "instance-22", "instance-76"]
            };
            var markupBuilder = SimpleTreeView({}).markupBuilder(testData);
            expect(markupBuilder.build()).toEqual(ExpectedMarkups.MultiNodeLevelsMarkup);
        });

        it('nested node levels', function () {
            var testData = {
                "UP"      : ["instance-1", "instance-2", "instance-3"],
                "DOWN"    : ["instance-1", "instance-2", "instance-3"],
                "STARTING": ["instance-1",
                    "instance-2",
                    { "STUCK": [ "instance-33", "instance-3343"]},
                    "instance-3"]
            };
            var markupBuilder = SimpleTreeView({}).markupBuilder(testData);
            expect(markupBuilder.build()).toEqual(ExpectedMarkups.NestedNodeLevelMarkup);
        });

    })
});