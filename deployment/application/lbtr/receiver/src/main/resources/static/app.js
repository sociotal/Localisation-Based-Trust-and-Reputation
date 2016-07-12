function fullScreen(e) {
    if (typeof e.requestFullScreen !== 'undefined') {
        e.requestFullScreen();
    } else if (typeof e.mozRequestFullScreen !== 'undefined') {
        e.mozRequestFullScreen();
    } else if (typeof e.webkitRequestFullScreen !== 'undefined') {
        e.webkitRequestFullScreen();
    }
}

const colors = ['red', 'green', 'gold'];
const factor = 1;
function svg_translate(d) {
    return 'translate(' + [(d.x + .5) * factor, (d.y + .5) * factor] + ')';
}

$body = document.querySelector('body');
$body.addEventListener('click', function (e) {
    if (!window.fullScreenEnabled) {
        e.stopPropagation();
        fullScreen($body);
    }
});

document.querySelector('#btn-clear').addEventListener('click', function (e) {
    e.stopPropagation();
    var that = this;
    that.setAttribute('disabled', 'disabled');
    var request = new XMLHttpRequest();
    request.open('POST', 'app/clear', true);
    request.onload = function () {
        that.removeAttribute('disabled');
    };
    setTimeout(function () {
        request.send();
    }, 200);
});

var svg = d3.select('#map')
    .append('svg')
    .attr('xmlns', 'http://www.w3.org/2000/svg')
    .attr('width', '100%')
    .attr('height', '100%')
    ;
var svgmap = svg.append('g')
    .attr('transform', 'scale(1.075 1.075) translate(-52 -41)');

var request = new XMLHttpRequest();
request.open('GET', 'app/anchors', true);

request.onload = function () {
    if (request.status >= 200 && request.status < 400) {
        var width = 0, height = 0;
        var anchorData = JSON.parse(request.responseText);
        anchorData.map(function (a) {
            width = Math.max(width, a.x);
            height = Math.max(height, a.y);
        });
        var sel = svgmap.selectAll('g.anchor').data(anchorData, function (d) {
            return d.name;
        });
        var g = sel.enter().append('g')
            .classed('anchor', true)
            .attr('data-name', function (d) {
                return d.name;
            })
            .attr('transform', svg_translate);
        g.append('circle')
            .attr('cx', 3)
            .attr('cy', 3)
            .attr('r', 3)
            .attr('fill', 'blue');
        g.append('text')
            .attr('fill', 'black')
            .attr('transform', 'translate(9 7)');
        svg.attr('viewBox', '0 0 ' + width + ' ' + height);
        // console.log('bbox', svgmap.node().getBBox());
    }
};
request.send();


function updateLocation() {
    var request = new XMLHttpRequest();
    request.open('GET', 'app/locations', true);
    request.onload = function () {
        if (request.status >= 200 && request.status < 400) {
            var locationData = JSON.parse(request.responseText);
            var sel;
            svgmap.selectAll('g.anchor').attr('opacity', .3).select('text').text('');
            sel = svgmap.selectAll('g.anchor')
                .data(locationData.anchors, function (d) {
                    return d.name;
                });
            sel.attr('opacity', 1);
            sel.select('text').text(function (d) {
                return d ? '' + d.rssi.toFixed(1) : '';
            });
            sel = svgmap.selectAll('g.mobile').data(locationData.locations);
            // build
            var g = sel.enter().append('g').classed('mobile', true);
            g.append('path')
                .attr('fill', 'none')
                .attr('d', 'M5,-5 l0,5 M-5,5 l5,0 M15,5 l-5,0 M5,15 l0,-5 M5,5 m-5, 0 a5,5 0 1,1 10,0 a5,5 0 1,1 -10,0');
            g.append('text')
                .attr('fill', 'black')
                .attr('transform', 'translate(17 9)');
            // clear
            sel.exit().remove();
            // update
            sel.attr('transform', function (d) {
                return d ? svg_translate(d) : null;
            });
            sel.select('path')
                .attr('stroke', function (d, i) {
                    return d ? colors[i] : 'none';
                });
            sel.select('text').text(function (d) {
                return d ? d.method : '';
            })
        }
        setTimeout(updateLocation, 2000);
    };
    request.send();
}
setTimeout(updateLocation, 2000);
