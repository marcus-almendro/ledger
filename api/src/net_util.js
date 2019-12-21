import { createConnection } from 'net';
import Promise from 'bluebird';

function checkConnection(host, port, timeout) {
    return new Promise((resolve, reject) => {
        timeout = timeout || 1000;
        var ok = false;

        var tryConnection = () => {
            console.log('Waiting for ' + host + ' at ' + port);
            createConnection(port, host, () => {
                ok = true;
                resolve();
            }).on("error", err => {
                setTimeout(() => {
                    if(!ok) 
                        tryConnection();
                }, timeout);
            });
        };

        tryConnection();

    });
}

export {checkConnection};