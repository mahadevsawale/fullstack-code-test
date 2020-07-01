const listContainer = document.querySelector('#service-list');
let servicesRequest = new Request('/service');
fetch(servicesRequest)
.then(function(response) { return response.json(); })
.then(function(serviceList) {
  serviceList.forEach(service => {
    var li = document.createElement("li");
    li.appendChild(document.createTextNode(service.name + ': ' + service.url+ ': ' + service.status));
    listContainer.appendChild(li);
  });
});

const saveButton = document.querySelector('#post-service');
saveButton.onclick = evt =>
{
    let name = document.querySelector('#name').value;
    let url = document.querySelector('#url').value;
    fetch('/service', {
        method: 'post',
        headers: {
            'Accept': 'application/json, text/plain, */*',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({name: name, url: url})
    }).then(res => location.reload());
}

const deleteButton = document.querySelector('#delete-service');
deleteButton.onclick = evt =>
{
    let name_del = document.querySelector('#name_del').value;
    fetch('/service', {
        method: 'delete',
        headers: {
            'Accept': 'application/json, text/plain, */*',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({name:name_del})
    }).then(res=> location.reload());
}