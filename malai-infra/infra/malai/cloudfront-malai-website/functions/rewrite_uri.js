function queryStringOf(request) {
    var parts = [];
    Object.keys(request.querystring).forEach(function (key) {
        var entry = request.querystring[key];
        if (entry.multiValue) {
            entry.multiValue.forEach(function (item) {
                parts.push(key + '=' + item.value);
            });
        } else {
            parts.push(key + '=' + entry.value);
        }
    });
    return parts.length === 0 ? '' : '?' + parts.join('&');
}

function namesAFile(uri) {
    return uri.includes('.');
}

function canonicalPathOf(uri) {
    if (uri.endsWith('/') || namesAFile(uri)) {
        return uri;
    }
    return uri + '/';
}

function movedPermanentlyTo(location) {
    return {
        statusCode: 301,
        statusDescription: 'Moved Permanently',
        headers: {
            location: {
                value: location
            }
        }
    };
}

function handler(event) {
    var request = event.request;
    var host = request.headers.host.value;
    var uri = request.uri;
    var canonicalPath = canonicalPathOf(uri);
    var queryString = queryStringOf(request);

    if (host.startsWith('www.')) {
        return movedPermanentlyTo('https://' + host.slice(4) + canonicalPath + queryString);
    }

    if (canonicalPath !== uri) {
        return movedPermanentlyTo('https://' + host + canonicalPath + queryString);
    }

    if (uri.endsWith('/')) {
        request.uri += 'index.html';
    }

    return request;
}
