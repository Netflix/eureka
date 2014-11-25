$(function () {
    // load header
    $('#header').load('header.html', updateNav);

    // load footer
});

function updateNav() {
    var navBarElm = $('#navbar');
    var currentPath = document.location.pathname;
    if (currentPath.indexOf('dashboard') > -1) {
        navBarElm.find('li.registry-nav').addClass('active');
    } else if (currentPath.indexOf('cluster') > -1) {
        navBarElm.find('li.cluster-nav').addClass('active');
    } else if (currentPath.indexOf('metrics') > -1) {
        navBarElm.find('li.metrics-nav').addClass('active');
    }
}
