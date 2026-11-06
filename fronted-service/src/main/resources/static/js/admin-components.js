
document.addEventListener("DOMContentLoaded", () => {
    const createDialog = document.getElementById("createDialog");
    const editDialog = document.getElementById("editDialog");
    const addBtn = document.getElementById("addBtn");
    const editForm = document.getElementById("editForm");

    addBtn?.addEventListener("click", () => {
        createDialog?.showModal();
    });

    document.querySelectorAll(".js-edit").forEach(button => {
        button.addEventListener("click", () => {
            document.getElementById("edit-id").value = button.dataset.id || "";
            document.getElementById("edit-name").value = button.dataset.name || "";
            document.getElementById("edit-brand").value = button.dataset.brand || "";
            document.getElementById("edit-price").value = button.dataset.price || "";
            document.getElementById("edit-stock").value = button.dataset.stock || "";

            editDialog?.showModal();
        });
    });

    document.querySelectorAll(".js-close").forEach(button => {
        button.addEventListener("click", () => {
            button.closest("dialog")?.close();
        });
    });

    [createDialog, editDialog].forEach(dialog => {
        dialog?.addEventListener("click", event => {
            if (event.target === dialog) {
                dialog.close();
            }
        });
    });

    document.querySelectorAll(".js-delete-form").forEach(form => {
        form.addEventListener("submit", event => {
            if (!confirm("Удалить комплектующее?")) {
                event.preventDefault();
            }
        });
    });
});
